package de.entdecker.hannover

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import de.entdecker.hannover.GameState.GameEvent
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.min

class MainActivity : AppCompatActivity(), GameState.Listener {

    private lateinit var map: MapView
    private lateinit var tvPct: TextView
    private lateinit var pbPct: LinearProgressIndicator
    private lateinit var tvKm: TextView
    private lateinit var tvPois: TextView
    private lateinit var tvAch: TextView
    private lateinit var tvEyes: TextView
    private lateinit var banner: TextView
    private lateinit var btnTrack: View
    private lateinit var ivTrack: ImageView
    private lateinit var tvTrack: TextView

    private var uncial: Typeface? = null
    private var cinzel: Typeface? = null
    private var cinzelBold: Typeface? = null

    private var follow = true
    private var online = true
    private lateinit var offlineOverlay: OfflineMapOverlay
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { runOnUiThread { online = isOnlineNow(); applyMapMode() } }
        override fun onLost(network: Network) { runOnUiThread { online = isOnlineNow(); applyMapMode() } }
    }
    private var centeredOnce = false

    private val bannerQueue = ArrayDeque<Pair<String, Int>>()
    private var bannerBusy = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPermission()
        if (ok) startTracking()
        else showBanner("Sin permiso de ubicación\nla niebla no se puede despejar", color(R.color.muted))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        uncial = ResourcesCompat.getFont(this, R.font.uncial)
        cinzel = ResourcesCompat.getFont(this, R.font.cinzel)
        cinzelBold = ResourcesCompat.getFont(this, R.font.cinzel_bold)

        map = findViewById(R.id.map)
        tvPct = findViewById(R.id.tvPct)
        pbPct = findViewById(R.id.pbPct)
        tvKm = findViewById(R.id.tvKm)
        tvPois = findViewById(R.id.tvPois)
        tvAch = findViewById(R.id.tvAch)
        tvEyes = findViewById(R.id.tvEyes)
        banner = findViewById(R.id.banner)
        btnTrack = findViewById(R.id.btnTrack)
        ivTrack = findViewById(R.id.ivTrack)
        tvTrack = findViewById(R.id.tvTrack)

        setupMap()

        btnTrack.setOnClickListener {
            if (GameState.tracking) stopService(Intent(this, TrackingService::class.java))
            else requestAndStart()
        }
        findViewById<ImageButton>(R.id.btnLocate).setOnClickListener {
            follow = true
            if (GameState.hasPos) {
                map.controller.animateTo(GeoPoint(GameState.posLat, GameState.posLon), 17.0, 600L)
            } else {
                showBanner("Aún no hay ubicación\nToca Explorar para empezar", color(R.color.muted))
            }
        }
        findViewById<ImageButton>(R.id.btnPin).setOnClickListener {
            if (GameState.hasPos) showAddPin(GameState.posLat, GameState.posLon, false)
            else showBanner("Mantén presionado el mapa\ndonde quieras poner el pin", color(R.color.gold))
        }
        findViewById<ImageButton>(R.id.btnAch).setOnClickListener { showAchievements() }
        findViewById<ImageButton>(R.id.btnShop).setOnClickListener { showShop() }
        findViewById<View>(R.id.eyesBox).setOnClickListener { showShop() }
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { showMenu(it) }

        map.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_MOVE) follow = false
            false
        }
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.minZoomLevel = 5.0
        map.maxZoomLevel = 19.0
        map.isTilesScaledToDpi = true

        applyMapTheme()
        map.overlayManager.tilesOverlay.loadingBackgroundColor = color(R.color.fog)
        map.overlayManager.tilesOverlay.loadingLineColor = color(R.color.fog)

        val d = resources.displayMetrics.density
        offlineOverlay = OfflineMapOverlay(d, cinzelBold)
        map.overlays.add(offlineOverlay)
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false
                showAddPin(p.latitude, p.longitude, true)
                return true
            }
        }))
        map.overlays.add(FogOverlay())
        map.overlays.add(PoiOverlay(d, cinzelBold) { poi, found -> onPoiTap(poi, found) })
        map.overlays.add(PinOverlay(this, d) { pin -> showPin(pin) })
        map.overlays.add(PlayerOverlay(d))

        online = isOnlineNow()
        try {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(netCallback)
        } catch (_: Exception) { }
        OfflineMap.onChange = { applyMapMode() }
        applyMapMode()

        if (GameState.hasPos) {
            map.controller.setZoom(16.5)
            map.controller.setCenter(GeoPoint(GameState.posLat, GameState.posLon))
            centeredOnce = true
        } else {
            map.controller.setZoom(14.5)
            map.controller.setCenter(GeoPoint(52.3745, 9.7385)) // Kröpcke
        }
    }

    private fun isOnlineNow(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Sin internet (o si se prefiere) se dibuja el mapa descargado en vez de los mosaicos. */
    private fun applyMapMode() {
        val useOffline = OfflineMap.ready && (OfflineMap.preferOffline || !online)
        map.overlayManager.tilesOverlay.isEnabled = !useOffline
        offlineOverlay.isEnabled = useOffline
        map.invalidate()
    }

    override fun onDestroy() {
        try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCallback) } catch (_: Exception) { }
        if (OfflineMap.onChange != null) OfflineMap.onChange = null
        super.onDestroy()
    }

    /** Aplica el estilo de mapa equipado en la tienda. */
    private fun applyMapTheme() {
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(Shop.mapMatrix()))
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        GameState.uiVisible = true
        GameState.addListener(this)
        updateHud()
        map.invalidate()
    }

    override fun onPause() {
        GameState.uiVisible = false
        GameState.removeListener(this)
        map.onPause()
        super.onPause()
    }

    override fun onGameUpdate(ev: GameEvent) {
        updateHud()
        if (ev.moved && GameState.hasPos) {
            val here = GeoPoint(GameState.posLat, GameState.posLon)
            if (!centeredOnce) {
                centeredOnce = true
                map.controller.animateTo(here, 17.0, 800L)
            } else if (follow && !GameState.testMode) {
                map.controller.animateTo(here)
            }
        }
        for (p in ev.discovered) {
            val c = if (p.isGreen) color(R.color.moss) else color(R.color.gold)
            showBanner("ᛟ Descubriste ᛟ\n${p.name}\n+${GameState.rewardFor(p)} Ojos de Greydwarf", c)
        }
        for (a in ev.achievements) {
            showBanner("ᛉ Nueva hazaña ᛉ\n${a.title}\n+${a.reward} Ojos de Greydwarf", color(R.color.ember))
        }
        if (ev.discovered.isEmpty() && ev.achievements.isEmpty() && ev.eyes > 0) {
            showBanner("+${ev.eyes} Ojos de Greydwarf\npor caminar", color(R.color.eye))
        }
        map.invalidate()
    }

    private fun updateHud() {
        val s = GameState.stats()
        tvPct.text = "${fmt(s.hPct, 2)} %"
        pbPct.setProgressCompat((s.hPct * 10).toInt().coerceIn(0, 1000), true)
        tvKm.text = fmt(s.walkedKm, 1)
        tvPois.text = "${s.hPois}/${s.hPoisTotal}"
        tvAch.text = "${GameState.unlocked.size}/${Achievements.all.size}"
        tvEyes.text = "${GameState.eyes}"
        if (GameState.tracking) {
            tvTrack.text = "DETENER"
            ivTrack.setImageResource(R.drawable.ic_stop)
            btnTrack.contentDescription = "Detener exploración"
        } else {
            tvTrack.text = "EXPLORAR"
            ivTrack.setImageResource(R.drawable.ic_play)
            btnTrack.contentDescription = "Explorar"
        }
    }

    // ------------------------------------------------------------ permisos y GPS

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestAndStart() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startTracking() else permLauncher.launch(missing.toTypedArray())
    }

    private fun startTracking() {
        follow = true
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
        showBanner("La antorcha está encendida\nPuedes bloquear el teléfono", color(R.color.ember))
    }

    // ------------------------------------------------------------ lugares

    private fun onPoiTap(p: Poi, found: Boolean) {
        if (!found) {
            showBanner("Lugar oculto en la niebla\nAcércate para revelarlo", color(R.color.gold))
            return
        }
        val where = if (p.scope == Scope.HANNOVER) "Hannover" else "Alemania"
        vikingDialog(p.name)
            .setMessage("${p.cat.label} · $where\nDescubierto")
            .setNeutralButton("Poner pin aquí") { _, _ -> showAddPin(p.lat, p.lon, false) }
            .setPositiveButton("Cerrar", null)
            .show()
    }

    // ------------------------------------------------------------ pines

    private fun showAddPin(lat: Double, lon: Double, fromLongPress: Boolean) {
        val dlg = BottomSheetDialog(this)
        val root = sheetRoot()
        root.addView(heading("Nuevo pin"))
        root.addView(caption("Elige qué quieres recordar en este punto"))

        var selected = PinType.FOOD
        val cells = HashMap<PinType, LinearLayout>()
        val types = PinType.values()
        var row: LinearLayout? = null
        types.forEachIndexed { i, t ->
            if (i % 4 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                root.addView(row)
            }
            val cell = typeCell(t)
            cell.setOnClickListener {
                selected = t
                for ((type, v) in cells) {
                    v.setBackgroundResource(if (type == t) R.drawable.chip_type_on else R.drawable.chip_type)
                }
            }
            cells[t] = cell
            row?.addView(cell)
        }
        cells[selected]?.setBackgroundResource(R.drawable.chip_type_on)

        val note = EditText(this).apply {
            hint = "Nota rápida (opcional): ej. Döner bueno y barato"
            setHintTextColor(color(R.color.muted))
            setTextColor(color(R.color.parchment))
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setBackgroundResource(R.drawable.field)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            maxLines = 2
        }
        root.addView(note, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })

        root.addView(primaryButton("GUARDAR PIN") {
            val p = PinStore.add(selected, lat, lon, note.text.toString())
            dlg.dismiss()
            map.invalidate()
            showBanner("Pin guardado\n${p.title}", color(R.color.gold))
        })

        if (fromLongPress && GameState.testMode) {
            root.addView(secondaryButton("Modo prueba: simular que estoy aquí") {
                dlg.dismiss()
                GameState.simulate(lat, lon)
            })
        }

        dlg.setContentView(root)
        showSheet(dlg)
    }

    private fun typeCell(t: PinType): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundResource(R.drawable.chip_type)
        setPadding(dp(4), dp(10), dp(4), dp(8))
        isClickable = true
        isFocusable = true
        contentDescription = t.label
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(3); marginEnd = dp(3)
        }
        addView(pinBadge(t, 40))
        addView(TextView(context).apply {
            text = t.label
            textSize = 11f
            typeface = cinzelBold
            gravity = Gravity.CENTER
            setTextColor(color(R.color.parchment))
            setPadding(0, dp(6), 0, 0)
            maxLines = 1
        })
    }

    private fun pinBadge(t: PinType, sizeDp: Int): ImageView = ImageView(this).apply {
        setImageResource(t.icon)
        setColorFilter(color(R.color.parchment))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(t.color)
            setStroke(dp(2), color(R.color.bronze))
        }
        val pad = dp(sizeDp) / 4
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun showPin(p: Pin) {
        val date = SimpleDateFormat("d MMM yyyy, HH:mm", ES).format(Date(p.created))
        val dist = if (GameState.hasPos) {
            val m = Geo.distance(GameState.posLat, GameState.posLon, p.lat, p.lon)
            "\nA " + (if (m < 1000) "${m.toInt()} m" else "${fmt(m / 1000, 1)} km") + " de ti"
        } else ""
        val body = (if (p.note.isNotBlank()) "${p.note}\n\n" else "") + "${p.type.label} · guardado el $date$dist"
        vikingDialog(p.title)
            .setMessage(body)
            .setPositiveButton("Cómo llegar") { _, _ -> openDirections(p) }
            .setNegativeButton("Eliminar") { _, _ -> confirmDeletePin(p) }
            .setNeutralButton("Cerrar", null)
            .show()
    }

    private fun confirmDeletePin(p: Pin) {
        vikingDialog("Eliminar pin")
            .setMessage("Se borra «${p.title}» del mapa.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                PinStore.remove(p)
                map.invalidate()
                showBanner("Pin eliminado", color(R.color.muted))
            }
            .show()
    }

    private fun openDirections(p: Pin) {
        val uri = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(p.title)})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            showBanner("No hay una app de mapas instalada", color(R.color.muted))
        }
    }

    private fun showPinList() {
        val dlg = BottomSheetDialog(this)
        val root = sheetRoot()
        root.addView(heading("Mis pines · ${PinStore.pins.size}"))
        if (PinStore.pins.isEmpty()) {
            root.addView(caption("Todavía no tienes pines. Mantén presionado el mapa o toca el escudo del pin para poner uno."))
        }
        val sorted = if (GameState.hasPos) PinStore.pins.sortedBy {
            Geo.distance(GameState.posLat, GameState.posLon, it.lat, it.lon)
        } else PinStore.pins.sortedByDescending { it.created }
        for (p in sorted) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dlg.dismiss()
                    follow = false
                    map.controller.animateTo(GeoPoint(p.lat, p.lon), 17.5, 600L)
                }
            }
            row.addView(pinBadge(p.type, 36))
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(TextView(this).apply {
                text = p.title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.parchment))
            })
            val dist = if (GameState.hasPos) {
                val m = Geo.distance(GameState.posLat, GameState.posLon, p.lat, p.lon)
                " · " + if (m < 1000) "${m.toInt()} m" else "${fmt(m / 1000, 1)} km"
            } else ""
            texts.addView(TextView(this).apply {
                text = p.type.label + dist
                textSize = 12f
                setTextColor(color(R.color.muted))
            })
            row.addView(texts)
            root.addView(row)
        }
        dlg.setContentView(NestedScrollView(this).apply { addView(root) })
        showSheet(dlg)
    }

    // ------------------------------------------------------------ menú

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 4, 0, "Mis pines")
        pm.menu.add(0, 1, 1, "Actualizar lugares de Hannover")
        pm.menu.add(0, 2, 2, "Modo prueba").apply {
            isCheckable = true
            isChecked = GameState.testMode
        }
        pm.menu.add(0, 5, 3, when {
            !OfflineMap.ready -> "Descargar mapa de Hannover"
            OfflineMap.missingNames -> "Actualizar mapa (agrega nombres de calles)"
            else -> "Actualizar mapa descargado"
        })
        if (OfflineMap.ready) {
            pm.menu.add(0, 6, 4, "Usar siempre el mapa descargado").apply {
                isCheckable = true
                isChecked = OfflineMap.preferOffline
            }
        }
        pm.menu.add(0, 3, 5, "Borrar progreso")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                4 -> showPinList()
                1 -> {
                    showBanner("Consultando a los cartógrafos…", color(R.color.muted))
                    GameState.refreshPois { msg -> showBanner(msg, color(R.color.moss)) }
                }
                2 -> {
                    GameState.testMode = !GameState.testMode
                    showBanner(
                        if (GameState.testMode) "Modo prueba activado\nMantén presionado el mapa y elige «simular»"
                        else "Modo prueba desactivado",
                        color(R.color.gold)
                    )
                }
                5 -> confirmDownload()
                6 -> {
                    OfflineMap.preferOffline = !OfflineMap.preferOffline
                    applyMapMode()
                    showBanner(
                        if (OfflineMap.preferOffline) "Usando el mapa descargado\nNo gasta datos"
                        else "Mapa en línea cuando hay internet",
                        color(R.color.gold)
                    )
                }
                3 -> confirmReset()
            }
            true
        }
        pm.show()
    }

    private fun confirmDownload() {
        if (OfflineMap.downloading) {
            showBanner("El mapa ya se está descargando", color(R.color.muted))
            return
        }
        val msg = if (OfflineMap.ready)
            "Vuelve a descargar las calles (con sus nombres), senderos, agua, parques y bosques de Hannover para tener los cambios más recientes."
        else
            "Se descargan las calles con sus nombres, senderos, ríos, lagos, parques y bosques de Hannover (unos 5 a 15 MB). Después el mapa funciona sin internet. Usa Wi‑Fi si puedes; tarda 1 o 2 minutos."
        vikingDialog(if (OfflineMap.ready) "Actualizar mapa" else "Mapa sin conexión")
            .setMessage(msg)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Descargar") { _, _ -> startDownload() }
            .show()
    }

    private fun startDownload() {
        if (!PoiRepository.hasCache(this)) {
            GameState.refreshPois { }
        }
        OfflineMap.download(
            progress = { step -> showBanner(step, color(R.color.muted)) },
            done = { ok, text -> showBanner(text, color(if (ok) R.color.moss else R.color.muted)) }
        )
    }

    private fun confirmReset() {
        vikingDialog("Borrar progreso")
            .setMessage("La niebla vuelve a cubrir todo el mapa y se pierden lugares, kilómetros y hazañas. Tus pines, tus Ojos de Greydwarf y lo que compraste se conservan. No se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar todo") { _, _ ->
                GameState.resetProgress()
                map.invalidate()
                showBanner("Progreso borrado", color(R.color.muted))
            }
            .show()
    }

    // ------------------------------------------------------------ aviso flotante

    private fun showBanner(text: String, textColor: Int) {
        bannerQueue.addLast(text to textColor)
        if (!bannerBusy) nextBanner()
    }

    private fun nextBanner() {
        val item = bannerQueue.removeFirstOrNull()
        if (item == null) {
            bannerBusy = false
            return
        }
        bannerBusy = true
        banner.text = item.first
        banner.setTextColor(item.second)
        banner.alpha = 0f
        banner.scaleX = 0.92f
        banner.scaleY = 0.92f
        banner.visibility = View.VISIBLE
        banner.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()
        banner.postDelayed({
            banner.animate().alpha(0f).setDuration(280).withEndAction {
                banner.visibility = View.GONE
                nextBanner()
            }.start()
        }, 2600)
    }

    // ------------------------------------------------------------ hazañas

    private fun showAchievements() {
        val s = GameState.stats()
        val root = sheetRoot()

        root.addView(heading("Tu saga"))
        root.addView(statRow("Hannover explorado", "${fmt(s.hPct, 2)} %"))
        root.addView(statRow("Lugares de Hannover", "${s.hPois} de ${s.hPoisTotal}"))
        root.addView(statRow("Parques y bosques", "${s.hGreen} de ${s.hGreenTotal}"))
        root.addView(statRow("Kilómetros caminados", fmt(s.walkedKm, 1)))
        root.addView(statRow("Días explorando", "${s.days}"))
        root.addView(statRow("Alemania explorada", "${fmt(s.gPct, 4)} %"))
        root.addView(statRow("Monumentos de Alemania", "${s.gLand} de ${s.gLandTotal}"))
        root.addView(statRow("Ciudades visitadas", "${s.cities} de ${s.citiesTotal}"))
        root.addView(statRow("Pines guardados", "${PinStore.pins.size}"))
        root.addView(statRow("Ojos de Greydwarf ganados", "${GameState.eyesEarned}"))

        val dateFmt = SimpleDateFormat("d MMM yyyy", ES)
        for (sec in Section.values()) {
            val list = Achievements.all.filter { it.section == sec }
            val done = list.count { it.id in GameState.unlocked }
            root.addView(heading("ᛟ ${sec.title} · $done/${list.size}"))
            for (a in list) {
                val unlockedAt = GameState.unlocked[a.id]
                root.addView(achievementRow(a, s, unlockedAt?.let { dateFmt.format(Date(it)) }))
            }
        }

        val dlg = BottomSheetDialog(this)
        dlg.setContentView(NestedScrollView(this).apply { addView(root) })
        dlg.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.75).toInt()
        showSheet(dlg)
    }

    private fun achievementRow(a: Achievement, s: Stats, unlockedDate: String?): View {
        val done = unlockedDate != null
        val v = a.value(s)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(this).apply {
            text = if (done) "ᛉ ${a.title}" else a.title
            textSize = 15f
            typeface = cinzelBold
            setTextColor(color(if (done) R.color.gold else R.color.parchment))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = if (done) unlockedDate else {
                val unit = if (a.unit.isEmpty()) "" else " ${a.unit}"
                if (a.unit.startsWith("de ")) "${fmt(min(v, a.target), 0)} ${a.unit}"
                else "${fmt(min(v, a.target), a.decimals)} / ${fmt(a.target, a.decimals)}$unit"
            }
            textSize = 12f
            setTextColor(color(R.color.muted))
        })
        box.addView(top)
        box.addView(TextView(this).apply {
            text = "${a.desc} · +${a.reward} ojos"
            textSize = 13f
            setTextColor(color(R.color.muted))
            setPadding(0, dp(2), 0, dp(6))
        })
        box.addView(LinearProgressIndicator(this).apply {
            max = 1000
            progress = ((min(v, a.target) / a.target) * 1000).toInt()
            setIndicatorColor(color(if (done) R.color.gold else R.color.ember))
            trackColor = color(R.color.track)
            trackThickness = dp(5)
            trackCornerRadius = dp(2)
        })
        return box
    }

    // ------------------------------------------------------------ mercader

    private fun showShop() {
        val dlg = BottomSheetDialog(this)
        val root = sheetRoot()

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(heading("El mercader").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_eye)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(6); topMargin = dp(10) }
        })
        val balance = TextView(this).apply {
            textSize = 20f
            typeface = cinzelBold
            setTextColor(color(R.color.eye))
            setPadding(0, dp(10), 0, 0)
        }
        head.addView(balance)
        root.addView(head)
        root.addView(caption(
            "Ganas ojos con la app abierta (1 por minuto), caminando (" +
                "${GameState.EYES_PER_WALK_STEP} cada ${GameState.WALK_STEP_M.toInt()} m), " +
                "descubriendo lugares y, sobre todo, con las hazañas."
        ))

        val refreshers = ArrayList<() -> Unit>()
        fun refreshAll() {
            balance.text = "${GameState.eyes}"
            for (r in refreshers) r()
        }

        for (cat in ShopCat.values()) {
            root.addView(heading("ᛟ ${cat.title}"))
            root.addView(caption(cat.hint))
            for (item in Shop.items.filter { it.cat == cat }) {
                root.addView(shopRow(item, refreshers) { refreshAll() })
            }
        }
        refreshAll()

        dlg.setContentView(NestedScrollView(this).apply { addView(root) })
        dlg.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
        showSheet(dlg)
    }

    private fun shopRow(item: ShopItem, refreshers: MutableList<() -> Unit>, onChange: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.chip_type)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row.addView(PreviewView(this) { c, w, h -> drawPreview(c, w, h, item) }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = item.name
            textSize = 15f
            typeface = cinzelBold
            setTextColor(color(R.color.parchment))
        })
        texts.addView(TextView(this).apply {
            text = item.desc
            textSize = 12f
            setTextColor(color(R.color.muted))
        })
        row.addView(texts)

        val action = TextView(this).apply {
            textSize = 13f
            typeface = cinzelBold
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
            isClickable = true
            isFocusable = true
        }
        row.addView(action)

        fun refresh() {
            when {
                Shop.isEquipped(item) -> {
                    action.text = "EN USO"
                    action.setTextColor(color(R.color.gold))
                    action.setBackgroundResource(R.drawable.chip_type_on)
                }
                Shop.owns(item) -> {
                    action.text = "USAR"
                    action.setTextColor(color(R.color.parchment))
                    action.setBackgroundResource(R.drawable.chip_type)
                }
                else -> {
                    action.text = "${item.price} ojos"
                    val canPay = GameState.eyes >= item.price
                    action.setTextColor(color(if (canPay) R.color.on_ember else R.color.muted))
                    action.setBackgroundResource(if (canPay) R.drawable.btn_ember else R.drawable.chip_type)
                }
            }
        }
        refreshers.add { refresh() }

        action.setOnClickListener {
            when {
                Shop.isEquipped(item) -> Unit
                Shop.owns(item) -> {
                    Shop.equip(item)
                    afterStyleChange(item)
                    onChange()
                }
                GameState.eyes < item.price -> {
                    val falta = item.price - GameState.eyes
                    showBanner("Te faltan $falta Ojos de Greydwarf\nSal a explorar", color(R.color.muted))
                }
                else -> {
                    vikingDialog(item.name)
                        .setMessage("¿Cambiar ${item.price} Ojos de Greydwarf por este estilo? Te quedarían ${GameState.eyes - item.price}.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Comprar") { _, _ ->
                            if (Shop.buy(item)) {
                                afterStyleChange(item)
                                showBanner("ᛟ Comprado ᛟ\n${item.name}", color(R.color.gold))
                            }
                            onChange()
                        }
                        .show()
                }
            }
        }
        return row
    }

    private fun afterStyleChange(item: ShopItem) {
        if (item.cat == ShopCat.MAP) applyMapTheme()
        updateHud()
        map.invalidate()
    }

    private val previewPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    /** Vistas previas: un trozo de mapa, la niebla, el marcador o un pin. */
    private fun drawPreview(c: android.graphics.Canvas, w: Float, h: Float, item: ShopItem) {
        val d = resources.displayMetrics.density
        val p = previewPaint
        p.colorFilter = null
        p.shader = null
        when (item.cat) {
            ShopCat.MAP -> {
                p.colorFilter = ColorMatrixColorFilter(Shop.mapMatrix(item.id))
                p.color = Color.rgb(242, 239, 233); c.drawRect(0f, 0f, w, h, p)
                p.color = Color.rgb(173, 209, 158); c.drawRect(0f, 0f, w * 0.45f, h * 0.55f, p)
                p.color = Color.rgb(170, 211, 223); c.drawRect(w * 0.6f, h * 0.5f, w, h, p)
                p.color = Color.rgb(248, 210, 140); c.drawRect(0f, h * 0.68f, w, h * 0.76f, p)
                p.color = Color.rgb(217, 208, 201); c.drawRect(w * 0.5f, 0f, w * 0.56f, h, p)
                p.colorFilter = null
            }
            ShopCat.FOG -> {
                val (base, vein) = Shop.fogColors(item.id)
                p.color = Color.rgb(242, 239, 233); c.drawRect(0f, 0f, w, h, p)
                p.color = base; c.drawRect(0f, 0f, w, h, p)
                p.color = Color.argb(40, Color.red(vein), Color.green(vein), Color.blue(vein))
                c.drawCircle(w * 0.3f, h * 0.35f, w * 0.22f, p)
                c.drawCircle(w * 0.7f, h * 0.7f, w * 0.18f, p)
                p.color = Color.rgb(173, 209, 158); c.drawCircle(w * 0.62f, h * 0.38f, w * 0.17f, p)
            }
            ShopCat.CURSOR -> {
                p.color = Color.argb(255, 20, 24, 31); c.drawRect(0f, 0f, w, h, p)
                CursorArt.draw(c, w / 2, h / 2, d * 0.95f, item.id, true)
            }
            ShopCat.PIN -> {
                p.color = Color.argb(255, 30, 22, 15); c.drawRect(0f, 0f, w, h, p)
                val r = 14f * d
                PinArt.drawFrame(c, w / 2, h / 2 - 4 * d, h / 2 + 18 * d, r, d, PinType.FOOD.color, item.id)
                val icon = ContextCompat.getDrawable(this, R.drawable.pin_food)?.mutate()
                if (icon != null) {
                    icon.setTint(color(R.color.parchment))
                    val half = (9.5f * d).toInt()
                    val cx = (w / 2).toInt(); val cy = (h / 2 - 4 * d).toInt()
                    icon.setBounds(cx - half, cy - half, cx + half, cy + half)
                    icon.draw(c)
                }
            }
        }
    }

    // ------------------------------------------------------------ piezas de interfaz

    private fun vikingDialog(title: String): MaterialAlertDialogBuilder {
        val t = TextView(this).apply {
            text = title
            textSize = 22f
            typeface = uncial
            setTextColor(color(R.color.gold))
            setPadding(dp(24), dp(22), dp(24), dp(4))
        }
        return MaterialAlertDialogBuilder(this)
            .setCustomTitle(t)
            .setBackground(ContextCompat.getDrawable(this, R.drawable.panel_wood))
    }

    private fun sheetRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.panel_sheet)
        setPadding(dp(20), dp(12), dp(20), dp(32))
    }

    private fun showSheet(dlg: BottomSheetDialog) {
        dlg.setOnShowListener {
            dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dlg.show()
    }

    private fun heading(t: String) = TextView(this).apply {
        text = t
        textSize = 21f
        typeface = uncial
        setTextColor(color(R.color.gold))
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(color(R.color.muted))
        setPadding(0, 0, 0, dp(10))
    }

    private fun statRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
        addView(TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(color(R.color.muted))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 14f
            typeface = cinzelBold
            setTextColor(color(R.color.parchment))
        })
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 16f
        typeface = cinzelBold
        letterSpacing = 0.08f
        gravity = Gravity.CENTER
        setTextColor(color(R.color.on_ember))
        setBackgroundResource(R.drawable.btn_ember)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(18)
        }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = cinzel
        gravity = Gravity.CENTER
        setTextColor(color(R.color.parchment))
        setBackgroundResource(R.drawable.chip_type)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
            topMargin = dp(10)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)
}
