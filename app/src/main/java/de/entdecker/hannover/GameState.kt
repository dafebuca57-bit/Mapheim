package de.entdecker.hannover

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

/** Todo el progreso del jugador. Se usa siempre desde el hilo principal. */
object GameState {

    interface Listener {
        fun onGameUpdate(ev: GameEvent)
    }

    class GameEvent(
        val discovered: List<Poi> = emptyList(),
        val achievements: List<Achievement> = emptyList(),
        val moved: Boolean = false,
        val eyes: Int = 0
    )

    // Ojos de Greydwarf: cuánto da cada cosa
    const val EYES_PASSIVE_PER_MIN = 1
    const val EYES_PER_WALK_STEP = 3
    const val WALK_STEP_M = 200.0
    const val EYES_POI = 10
    const val EYES_POI_FAMOUS = 25
    const val EYES_LANDMARK = 50
    const val EYES_CITY = 40

    private const val CELLS_FILE = "cells.bin"

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    val cells = HashSet<Long>()
    val blocks = HashMap<Long, MutableList<Long>>()
    var hannoverCells = 0
        private set
    var walkedM = 0.0
        private set
    var eyes = 0
        private set
    var eyesEarned = 0
        private set
    private var walkBucket = 0.0
    val discovered = HashSet<String>()
    val unlocked = LinkedHashMap<String, Long>()
    private val days = HashSet<String>()
    private val cities = HashSet<String>()

    var pois: List<Poi> = Curated.hannover + Curated.germany
        private set

    var hasPos = false
        private set
    var posLat = 0.0
        private set
    var posLon = 0.0
        private set

    private var hasAnchor = false
    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var anchorTime = 0L

    var uiVisible = false

    var testMode = false
        set(v) {
            field = v
            if (::prefs.isInitialized) prefs.edit().putBoolean("test", v).apply()
        }

    var tracking = false
        set(v) {
            field = v
            notifyListeners(GameEvent())
        }

    private var cellOut: DataOutputStream? = null
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("game", Context.MODE_PRIVATE)
        load()
        main.postDelayed(passiveTick, 60_000L)
        Thread {
            val all = try { PoiRepository.loadAll(app) } catch (e: Exception) { null }
            main.post { if (all != null) setPois(all) }
            if (!PoiRepository.hasCache(app)) {
                try {
                    PoiRepository.fetch(app)
                    val fresh = PoiRepository.loadAll(app)
                    main.post { setPois(fresh) }
                } catch (_: Exception) {
                    // sin internet: se usan los lugares escogidos a mano
                }
            }
        }.start()
    }

    /** Con la app abierta (o explorando) se gana 1 ojo por minuto. */
    private val passiveTick: Runnable = object : Runnable {
        override fun run() {
            if (uiVisible || tracking) {
                earn(EYES_PASSIVE_PER_MIN)
                save()
                notifyListeners(GameEvent())
            }
            main.postDelayed(this, 60_000L)
        }
    }

    private fun earn(n: Int) {
        eyes += n
        eyesEarned += n
    }

    /** Gasta ojos en la tienda. Devuelve false si no alcanza. */
    fun spend(n: Int): Boolean {
        if (n > eyes) return false
        eyes -= n
        save()
        notifyListeners(GameEvent())
        return true
    }

    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun notifyListeners(ev: GameEvent) {
        for (l in listeners) l.onGameUpdate(ev)
    }

    // ------------------------------------------------------------ ubicación

    /** Punto GPS real. Solo cuenta como caminado si va a paso de persona. */
    fun onLocation(lat: Double, lon: Double, accuracy: Float, timeMs: Long) {
        if (accuracy > 50f) return
        hasPos = true
        posLat = lat
        posLon = lon
        if (!hasAnchor) {
            setAnchor(lat, lon, timeMs)
        } else {
            val d = Geo.distance(anchorLat, anchorLon, lat, lon)
            val minStep = max(10.0, accuracy * 0.6)
            if (d >= minStep) {
                val dt = (timeMs - anchorTime) / 1000.0
                // hasta ~16 km/h: caminar o trotar. En carro o bus no suma km.
                if (dt > 0 && d / dt <= 4.5 && accuracy <= 30f) addWalk(d)
                setAnchor(lat, lon, timeMs)
            }
        }
        notifyListeners(process(lat, lon))
    }

    /** Modo prueba: "teletransporta" al jugador al punto tocado. */
    fun simulate(lat: Double, lon: Double) {
        if (hasPos) addWalk(min(Geo.distance(posLat, posLon, lat, lon), 3000.0))
        hasPos = true
        posLat = lat
        posLon = lon
        setAnchor(lat, lon, System.currentTimeMillis())
        notifyListeners(process(lat, lon))
    }

    private var pendingWalkEyes = 0

    private fun addWalk(d: Double) {
        walkedM += d
        walkBucket += d
        while (walkBucket >= WALK_STEP_M) {
            walkBucket -= WALK_STEP_M
            pendingWalkEyes += EYES_PER_WALK_STEP
        }
    }

    private fun setAnchor(lat: Double, lon: Double, t: Long) {
        hasAnchor = true
        anchorLat = lat
        anchorLon = lon
        anchorTime = t
    }

    private fun process(lat: Double, lon: Double): GameEvent {
        val newCells = ArrayList<Long>()
        for (k in Geo.cellsAround(lat, lon, Geo.REVEAL_M)) if (addCell(k)) newCells.add(k)
        appendCells(newCells)

        var gained = pendingWalkEyes
        pendingWalkEyes = 0
        val found = ArrayList<Poi>()
        for (p in pois) {
            if (p.id !in discovered && p.covers(lat, lon)) {
                discovered.add(p.id)
                found.add(p)
                gained += rewardFor(p)
            }
        }
        for (c in Curated.cities) {
            if (c.id !in cities && Geo.distance(c.lat, c.lon, lat, lon) <= c.radiusKm * 1000) {
                cities.add(c.id)
                gained += EYES_CITY
            }
        }
        days.add(dayFmt.format(Date()))
        val ach = checkAchievements()
        gained += ach.sumOf { it.reward }
        earn(gained)
        save()
        return GameEvent(found, ach, moved = true, eyes = gained)
    }

    fun rewardFor(p: Poi): Int = when {
        p.scope == Scope.GERMANY -> EYES_LANDMARK
        p.famous -> EYES_POI_FAMOUS
        else -> EYES_POI
    }

    private fun addCell(key: Long): Boolean {
        if (!cells.add(key)) return false
        blocks.getOrPut(Geo.blockKey(key)) { ArrayList() }.add(key)
        if (Geo.inHannover(Geo.centerLat(key), Geo.centerLon(key))) hannoverCells++
        return true
    }

    // ------------------------------------------------------------ lugares

    private fun setPois(all: List<Poi>) {
        pois = all
        // lugares nuevos que ya estaban en zonas despejadas cuentan como vistos
        for (p in all) {
            if (p.id !in discovered && cells.contains(Geo.cellKey(p.lat, p.lon)) && (!p.isArea || p.cat == Cat.SIGHT)) {
                discovered.add(p.id)
            }
        }
        val ach = checkAchievements()
        val gained = ach.sumOf { it.reward }
        earn(gained)
        save()
        notifyListeners(GameEvent(achievements = ach, eyes = gained))
    }

    fun refreshPois(done: (String) -> Unit) {
        Thread {
            try {
                val n = PoiRepository.fetch(app)
                val all = PoiRepository.loadAll(app)
                main.post {
                    setPois(all)
                    done("Se cargaron $n lugares, parques y bosques de Hannover")
                }
            } catch (e: Exception) {
                main.post { done("No se pudieron descargar los lugares. Revisa tu conexión e inténtalo otra vez.") }
            }
        }.start()
    }

    // ------------------------------------------------------------ logros

    fun stats(): Stats {
        var hP = 0; var hPT = 0; var hG = 0; var hGT = 0; var gL = 0; var gLT = 0
        for (p in pois) {
            val d = p.id in discovered
            if (p.scope == Scope.HANNOVER) {
                hPT++; if (d) hP++
                if (p.isGreen) { hGT++; if (d) hG++ }
            } else {
                gLT++; if (d) gL++
            }
        }
        return Stats(
            walkedKm = walkedM / 1000.0,
            hPct = min(100.0, 100.0 * hannoverCells / Geo.HANNOVER_CELLS),
            gPct = min(100.0, 100.0 * cells.size / Geo.GERMANY_CELLS),
            hPois = hP, hPoisTotal = hPT,
            hGreen = hG, hGreenTotal = hGT,
            gLand = gL, gLandTotal = gLT,
            cities = cities.size, citiesTotal = Curated.cities.size,
            days = days.size,
            herrenhausen = Curated.HERRENHAUSEN.count { it in discovered },
            altstadt = Curated.ALTSTADT.count { it in discovered },
            eilenriede = if (Curated.EILENRIEDE in discovered) 1 else 0,
            maschsee = if (Curated.MASCHSEE in discovered) 1 else 0
        )
    }

    private fun checkAchievements(): List<Achievement> {
        val s = stats()
        val now = System.currentTimeMillis()
        val fresh = Achievements.all.filter { it.id !in unlocked && it.value(s) >= it.target }
        for (a in fresh) unlocked[a.id] = now
        return fresh
    }

    // ------------------------------------------------------------ guardado

    private fun load() {
        cells.clear(); blocks.clear(); hannoverCells = 0
        val f = File(app.filesDir, CELLS_FILE)
        if (f.exists()) {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(f))).use { inp ->
                    while (true) {
                        val k = try { inp.readLong() } catch (e: EOFException) { break }
                        addCell(k)
                    }
                }
            } catch (_: Exception) { }
        }
        walkedM = prefs.getString("walked", "0")?.toDoubleOrNull() ?: 0.0
        walkBucket = prefs.getString("walkBucket", "0")?.toDoubleOrNull() ?: 0.0
        eyes = prefs.getInt("eyes", 0)
        eyesEarned = prefs.getInt("eyesEarned", 0)
        discovered.clear(); discovered.addAll(prefs.getStringSet("disc", emptySet()) ?: emptySet())
        days.clear(); days.addAll(prefs.getStringSet("days", emptySet()) ?: emptySet())
        cities.clear(); cities.addAll(prefs.getStringSet("cities", emptySet()) ?: emptySet())
        unlocked.clear()
        try {
            val o = JSONObject(prefs.getString("unlocked", "{}") ?: "{}")
            for (k in o.keys()) unlocked[k] = o.getLong(k)
        } catch (_: Exception) { }
        testMode = prefs.getBoolean("test", false)
    }

    private fun save() {
        val u = JSONObject()
        for ((k, v) in unlocked) u.put(k, v)
        prefs.edit()
            .putString("walked", walkedM.toString())
            .putString("walkBucket", walkBucket.toString())
            .putInt("eyes", eyes)
            .putInt("eyesEarned", eyesEarned)
            .putStringSet("disc", HashSet(discovered))
            .putStringSet("days", HashSet(days))
            .putStringSet("cities", HashSet(cities))
            .putString("unlocked", u.toString())
            .apply()
    }

    private fun appendCells(keys: List<Long>) {
        if (keys.isEmpty()) return
        try {
            val out = cellOut ?: DataOutputStream(
                BufferedOutputStream(FileOutputStream(File(app.filesDir, CELLS_FILE), true))
            ).also { cellOut = it }
            for (k in keys) out.writeLong(k)
            out.flush()
        } catch (_: Exception) { }
    }

    fun resetProgress() {
        try { cellOut?.close() } catch (_: Exception) { }
        cellOut = null
        File(app.filesDir, CELLS_FILE).delete()
        cells.clear(); blocks.clear(); hannoverCells = 0
        walkedM = 0.0
        walkBucket = 0.0
        discovered.clear(); unlocked.clear(); days.clear(); cities.clear()
        hasAnchor = false
        save()
        notifyListeners(GameEvent())
    }
}
