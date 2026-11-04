package de.entdecker.hannover

/**
 * Lugares escogidos a mano. Al abrir la app con internet se completan con los
 * datos de OpenStreetMap (cientos de lugares, parques y bosques de Hannover),
 * y las coordenadas de estos se corrigen con las de OSM cuando coinciden.
 */
object Curated {

    val HERRENHAUSEN = listOf("h_grosser_garten", "h_berggarten", "h_georgengarten")
    val ALTSTADT = listOf("h_marktkirche", "h_altes_rathaus", "h_leineschloss", "h_beginenturm", "h_leibnizhaus")
    const val EILENRIEDE = "h_eilenriede"
    const val MASCHSEE = "h_maschsee"

    val hannover: List<Poi> = listOf(
        // Centro y casco antiguo
        Poi.node("h_neues_rathaus", "Neues Rathaus", 52.3671, 9.7383, "neues rathaus", 90.0),
        Poi.node("h_marktkirche", "Marktkirche", 52.3717, 9.7354, "marktkirche"),
        Poi.node("h_altes_rathaus", "Altes Rathaus", 52.3724, 9.7359, "altes rathaus"),
        Poi.node("h_leineschloss", "Leineschloss", 52.3703, 9.7330, "leineschloss"),
        Poi.node("h_beginenturm", "Beginenturm", 52.3720, 9.7318, "beginenturm"),
        Poi.node("h_leibnizhaus", "Leibnizhaus", 52.3707, 9.7338, "leibnizhaus"),
        Poi.node("h_ballhof", "Ballhof", 52.3727, 9.7325, "ballhof"),
        Poi.node("h_kreuzkirche", "Kreuzkirche", 52.3734, 9.7328, "kreuzkirche"),
        Poi.node("h_nanas", "Las Nanas de Niki de Saint Phalle", 52.3729, 9.7296, "nanas"),
        Poi.node("h_aegidienkirche", "Aegidienkirche", 52.3693, 9.7390, "aegidienkirche"),
        Poi.node("h_kroepcke", "Kröpcke", 52.3745, 9.7385, "kröpcke"),
        Poi.node("h_oper", "Staatsoper Hannover", 52.3738, 9.7410, "opernhaus", 90.0),
        Poi.node("h_hbf", "Hauptbahnhof y Ernst-August", 52.3767, 9.7413, null, 100.0),
        Poi.node("h_waterloo", "Waterloosäule", 52.3664, 9.7294, "waterloo"),
        // Museos
        Poi.node("h_landesmuseum", "Landesmuseum Hannover", 52.3647, 9.7393, "landesmuseum"),
        Poi.node("h_sprengel", "Sprengel Museum", 52.3620, 9.7434, "sprengel", 90.0),
        Poi.node("h_kestner", "Museum August Kestner", 52.3687, 9.7362, "kestner"),
        Poi.node("h_historisches", "Historisches Museum", 52.3712, 9.7322, "historisches museum"),
        // Más allá del centro
        Poi.node("h_welfenschloss", "Welfenschloss (Universidad)", 52.3823, 9.7178, "welfenschloss", 100.0),
        Poi.node("h_schloss_herrenhausen", "Schloss Herrenhausen", 52.3915, 9.6990, "schloss herrenhausen", 100.0),
        Poi.node("h_zoo", "Erlebnis-Zoo Hannover", 52.3843, 9.7712, "zoo", 250.0),
        Poi.node("h_arena", "Heinz von Heiden Arena", 52.3601, 9.7312, "arena", 160.0),
        Poi.node("h_christuskirche", "Christuskirche", 52.3836, 9.7290, "christuskirche"),
        Poi.node("h_lindener_berg", "Lindener Berg", 52.3640, 9.7090, null, 300.0),
        Poi.node("h_expo", "Expo Plaza", 52.3195, 9.8170, "expo plaza", 180.0),

        // Parques, jardines y bosques
        Poi.area("h_grosser_garten", "Großer Garten", Cat.PARK, 52.3880, 9.6945, 52.3935, 9.7020, "großer garten"),
        Poi.area("h_berggarten", "Berggarten", Cat.PARK, 52.3925, 9.7010, 52.3965, 9.7075, "berggarten"),
        Poi.area("h_georgengarten", "Georgengarten", Cat.PARK, 52.3815, 9.7075, 52.3905, 9.7175, "georgengarten"),
        Poi.area("h_welfengarten", "Welfengarten", Cat.PARK, 52.3805, 9.7160, 52.3845, 9.7215, "welfengarten"),
        Poi.area("h_maschpark", "Maschpark", Cat.PARK, 52.3640, 9.7350, 52.3675, 9.7420, "maschpark"),
        Poi.area("h_maschsee", "Maschsee", Cat.PARK, 52.3420, 9.7365, 52.3650, 9.7540, "maschsee"),
        Poi.area("h_stadtpark", "Stadtpark", Cat.PARK, 52.3730, 9.7900, 52.3800, 9.8020, "stadtpark"),
        Poi.area("h_hermann_loens", "Hermann-Löns-Park", Cat.PARK, 52.3740, 9.8100, 52.3830, 9.8300, "hermann-löns"),
        Poi.area("h_eilenriede", "Eilenriede", Cat.FOREST, 52.3600, 9.7480, 52.4040, 9.8030, "eilenriede"),
        Poi.area("h_tiergarten", "Tiergarten", Cat.FOREST, 52.3570, 9.8280, 52.3690, 9.8560, "tiergarten")
    )

    val germany: List<Poi> = listOf(
        // Cerca de Hannover
        Poi.landmark("g_marienburg", "Schloss Marienburg", 52.1700, 9.7639),
        Poi.landmark("g_steinhuder_meer", "Steinhuder Meer", 52.4580, 9.3450, 2500.0),
        Poi.landmark("g_hameln", "Casco antiguo de Hameln", 52.1036, 9.3583, 450.0),
        Poi.landmark("g_hildesheim_dom", "Catedral de Hildesheim", 52.1491, 9.9468),
        Poi.landmark("g_celle", "Castillo de Celle", 52.6247, 10.0786),
        Poi.landmark("g_autostadt", "Autostadt Wolfsburg", 52.4301, 10.7915, 500.0),
        Poi.landmark("g_braunschweig", "Burg Dankwarderode", 52.2638, 10.5233),
        Poi.landmark("g_goslar", "Kaiserpfalz Goslar", 51.9034, 10.4268),
        Poi.landmark("g_brocken", "Brocken (Harz)", 51.7991, 10.6156, 600.0),
        Poi.landmark("g_goettingen", "Gänseliesel de Göttingen", 51.5334, 9.9356),
        Poi.landmark("g_hermann", "Hermannsdenkmal", 51.9113, 8.8394),
        Poi.landmark("g_externsteine", "Externsteine", 51.8688, 8.9173),
        // Norte
        Poi.landmark("g_elbphilharmonie", "Elbphilharmonie", 53.5413, 9.9842),
        Poi.landmark("g_wunderland", "Miniatur Wunderland", 53.5437, 9.9886, 250.0),
        Poi.landmark("g_bremen", "Plaza del Mercado de Bremen", 53.0758, 8.8072),
        Poi.landmark("g_holstentor", "Holstentor (Lübeck)", 53.8664, 10.6797),
        Poi.landmark("g_schwerin", "Castillo de Schwerin", 53.6242, 11.4185),
        Poi.landmark("g_ruegen", "Acantilados de Rügen", 54.5735, 13.6627, 800.0),
        // Berlín y este
        Poi.landmark("g_brandenburger", "Puerta de Brandeburgo", 52.5163, 13.3777),
        Poi.landmark("g_reichstag", "Reichstag", 52.5186, 13.3762),
        Poi.landmark("g_fernsehturm", "Fernsehturm de Berlín", 52.5208, 13.4094),
        Poi.landmark("g_eastside", "East Side Gallery", 52.5050, 13.4397, 600.0),
        Poi.landmark("g_sanssouci", "Palacio de Sanssouci", 52.4035, 13.0389, 500.0),
        Poi.landmark("g_frauenkirche", "Frauenkirche de Dresde", 51.0519, 13.7415),
        Poi.landmark("g_zwinger", "Zwinger de Dresde", 51.0530, 13.7337),
        Poi.landmark("g_wartburg", "Castillo de Wartburg", 50.9665, 10.3060),
        // Oeste
        Poi.landmark("g_koelner_dom", "Catedral de Colonia", 50.9413, 6.9583),
        Poi.landmark("g_aachen", "Catedral de Aquisgrán", 50.7747, 6.0839),
        Poi.landmark("g_loreley", "Loreley", 50.1395, 7.7287, 600.0),
        Poi.landmark("g_porta_nigra", "Porta Nigra (Tréveris)", 49.7597, 6.6440),
        Poi.landmark("g_roemer", "Römer de Frankfurt", 50.1106, 8.6821),
        Poi.landmark("g_heidelberg", "Castillo de Heidelberg", 49.4106, 8.7153),
        // Sur
        Poi.landmark("g_nuernberg", "Castillo Imperial de Núremberg", 49.4577, 11.0755),
        Poi.landmark("g_ulm", "Catedral de Ulm", 48.3985, 9.9925),
        Poi.landmark("g_marienplatz", "Marienplatz (Múnich)", 48.1374, 11.5755),
        Poi.landmark("g_neuschwanstein", "Castillo de Neuschwanstein", 47.5576, 10.7498, 450.0),
        Poi.landmark("g_zugspitze", "Zugspitze", 47.4211, 10.9853, 800.0),
        Poi.landmark("g_koenigssee", "Königssee", 47.5536, 12.9786, 1200.0)
    )

    val cities: List<City> = listOf(
        City("c_berlin", "Berlín", 52.5200, 13.4050, 12.0),
        City("c_hamburg", "Hamburgo", 53.5511, 9.9937, 10.0),
        City("c_muenchen", "Múnich", 48.1372, 11.5756, 9.0),
        City("c_koeln", "Colonia", 50.9375, 6.9603, 8.0),
        City("c_frankfurt", "Fráncfort", 50.1109, 8.6821, 7.0),
        City("c_stuttgart", "Stuttgart", 48.7758, 9.1829, 6.0),
        City("c_duesseldorf", "Düsseldorf", 51.2277, 6.7735, 6.0),
        City("c_leipzig", "Leipzig", 51.3397, 12.3731, 6.0),
        City("c_dortmund", "Dortmund", 51.5136, 7.4653, 6.0),
        City("c_essen", "Essen", 51.4556, 7.0116, 6.0),
        City("c_bremen", "Bremen", 53.0793, 8.8017, 7.0),
        City("c_dresden", "Dresde", 51.0504, 13.7373, 7.0),
        City("c_nuernberg", "Núremberg", 49.4521, 11.0767, 6.0),
        City("c_braunschweig", "Brunswick", 52.2689, 10.5268, 5.0),
        City("c_wolfsburg", "Wolfsburgo", 52.4227, 10.7865, 4.0),
        City("c_hildesheim", "Hildesheim", 52.1508, 9.9511, 4.0),
        City("c_celle", "Celle", 52.6226, 10.0805, 3.0),
        City("c_goettingen", "Gotinga", 51.5413, 9.9158, 4.0),
        City("c_hameln", "Hameln", 52.1039, 9.3560, 3.0),
        City("c_bielefeld", "Bielefeld", 52.0302, 8.5325, 5.0),
        City("c_muenster", "Münster", 51.9607, 7.6261, 5.0),
        City("c_bonn", "Bonn", 50.7374, 7.0982, 5.0),
        City("c_heidelberg", "Heidelberg", 49.3988, 8.6724, 4.0),
        City("c_luebeck", "Lübeck", 53.8655, 10.6866, 4.0),
        City("c_kiel", "Kiel", 54.3233, 10.1228, 5.0),
        City("c_rostock", "Rostock", 54.0924, 12.0991, 5.0),
        City("c_kassel", "Kassel", 51.3127, 9.4797, 5.0),
        City("c_magdeburg", "Magdeburgo", 52.1205, 11.6276, 5.0),
        City("c_erfurt", "Erfurt", 50.9848, 11.0299, 4.0),
        City("c_potsdam", "Potsdam", 52.3906, 13.0645, 4.0),
        City("c_freiburg", "Friburgo", 47.9990, 7.8421, 4.0),
        City("c_aachen", "Aquisgrán", 50.7753, 6.0839, 4.0)
    )
}
