package com.zepinto.codenames

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Languages the game is available in. Each has its own UI texts and its own word list in assets. */
enum class Lang(val code: String, val flag: String, val label: String) {
    EN("en", "🇬🇧", "English"),
    PT("pt", "🇵🇹", "Português"),
    ES("es", "🇪🇸", "Español");

    val wordFile get() = "words_$code.txt"

    val strings: Strings
        get() = when (this) {
            EN -> English
            PT -> Portuguese
            ES -> Spanish
        }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }

        /** The phone's language if the game has it, English otherwise. */
        fun deviceDefault() = fromCode(Locale.getDefault().language) ?: EN
    }
}

val LocalStrings = staticCompositionLocalOf { English }

/** One block of the rules page: a heading and its lines. */
data class RulesSection(val emoji: String, val title: String, val lines: List<String>)

/** Every user-visible text. Adding a field forces all three languages to provide it. */
data class Strings(
    // home
    val tagline: String,
    val spymasterTitle: String,
    val spymasterDesc: String,
    val tableTitle: String,
    val tableDesc: String,
    val resumeGame: String,
    val twoPhonesHint: String,

    // teams and play
    val teamName: (Team) -> String,
    val teamLong: (Team) -> String,
    val turnOf: (Team) -> String,
    val agentsLabel: String,
    val endTurn: String,
    val newGame: String,
    val newGameTitle: String,
    val newGameText: String,
    val teamWins: (Team) -> String,
    val assassinHit: (Team) -> String,

    // spymaster phone
    val keyHiddenTitle: String,
    val keyHiddenHint: String,
    val showKey: String,
    val hideKey: String,
    val tableConnected: String,
    val tableWaiting: String,
    val connectTitle: String,
    val connectHelpNearby: String,
    val connectHelpWifi: String,
    val wifiAddress: String,
    val pinLabel: String,
    val pairTitle: (String) -> String,
    val pairText: (String) -> String,
    val accept: String,
    val allow: String,
    val reject: String,
    val nearbyUnavailable: String,
    val allowNearby: String,

    // table phone
    val joinTitle: String,
    val joinHint: String,
    val searching: String,
    val noneFound: String,
    val connecting: String,
    val pairingCode: (String) -> String,
    val connectionFailed: String,
    val pinLocked: String,
    val wifiTitle: String,
    val addressHint: String,
    val pinHint: String,
    val connect: String,
    val permissionText: String,
    val tapAWord: String,
    val revealWord: (String) -> String,
    val connectionLost: String,
    val waitingNewGame: String,

    // shared
    val leaveTitle: String,
    val leave: String,
    val stay: String,
    val cancel: String,
    val back: String,
    val rulesButton: String,
    val rulesTitle: String,
    val rulesSections: List<RulesSection>,
)

val English = Strings(
    tagline = "Two phones. One board. Find your agents.",
    spymasterTitle = "Spymaster phone",
    spymasterDesc = "Creates the game and shows the secret key to the two spymasters.",
    tableTitle = "Table phone",
    tableDesc = "Shows the board to everyone and takes the team's guesses.",
    resumeGame = "Resume game",
    twoPhonesHint = "You need two phones: one for the spymasters, one on the table.",

    teamName = { if (it == Team.RED) "Red" else "Blue" },
    teamLong = { if (it == Team.RED) "Red team" else "Blue team" },
    turnOf = { if (it == Team.RED) "Red team's turn" else "Blue team's turn" },
    agentsLabel = "agents left",
    endTurn = "Pass turn",
    newGame = "New game",
    newGameTitle = "Start a new game?",
    newGameText = "The current game will be lost.",
    teamWins = { if (it == Team.RED) "Red team wins!" else "Blue team wins!" },
    assassinHit = { if (it == Team.RED) "Red team picked the assassin!" else "Blue team picked the assassin!" },

    keyHiddenTitle = "Key hidden",
    keyHiddenHint = "Spymasters only: tap to show the key. Hide it again before anyone else looks.",
    showKey = "Show key",
    hideKey = "Hide key",
    tableConnected = "Table phone connected",
    tableWaiting = "Waiting for the table phone...",
    connectTitle = "Connect the table phone",
    connectHelpNearby = "On the other phone, tap \"Table phone\". This phone will show up in the list.",
    connectHelpWifi = "Same Wi-Fi instead? Type this address and PIN on the table phone.",
    wifiAddress = "Address",
    pinLabel = "PIN",
    pairTitle = { "Connect to $it?" },
    pairText = { "Both phones should show the code $it." },
    accept = "Accept",
    allow = "Allow",
    reject = "Reject",
    nearbyUnavailable = "Nearby connection is not available. Wi-Fi still works.",
    allowNearby = "Allow \"Nearby devices\" so the two phones can find each other.",

    joinTitle = "Join a game",
    joinHint = "The spymaster phone must have created a game first.",
    searching = "Looking for spymaster phones nearby...",
    noneFound = "None found yet.",
    connecting = "Connecting...",
    pairingCode = { "Code $it. Accept on the spymaster phone." },
    connectionFailed = "Could not connect. Try again.",
    pinLocked = "Too many wrong PINs. Wait a minute and try again.",
    wifiTitle = "Same Wi-Fi instead",
    addressHint = "Address, e.g. 192.168.1.20:8765",
    pinHint = "PIN",
    connect = "Connect",
    permissionText = "To find the other phone, allow \"Nearby devices\".",
    tapAWord = "Tap a word to pick it.",
    revealWord = { "Reveal \"$it\"" },
    connectionLost = "Connection lost. Reconnecting...",
    waitingNewGame = "Waiting for a new game...",

    leaveTitle = "Leave this game?",
    leave = "Leave",
    stay = "Stay",
    cancel = "Cancel",
    back = "Back",
    rulesButton = "Rules",
    rulesTitle = "Rules",
    rulesSections = listOf(
        RulesSection("🎯", "The goal", listOf(
            "Two teams, red and blue. Each team has a spymaster who knows which words belong to their team.",
            "Find all your team's agents before the other team finds theirs, and stay away from the assassin!",
        )),
        RulesSection("📱", "The two phones", listOf(
            "The spymaster phone creates the game and shows the secret key. Only the two spymasters look at it.",
            "The table phone sits in the middle and shows the 25 words to everyone. It only learns a card's colour once the card is picked.",
            "The phones talk to each other live, through Nearby (Bluetooth and Wi-Fi Direct) or over the same Wi-Fi.",
        )),
        RulesSection("🗣️", "Giving a clue", listOf(
            "On their turn the spymaster says ONE word and ONE number out loud: how many words on the board fit the clue.",
            "The clue can't be one of the words on the board. No other hints, no faces, no gestures.",
            "The app does not take the clue. It only shows whose turn it is, so the clue is said out loud at the table.",
        )),
        RulesSection("👆", "Guessing", listOf(
            "The team talks it over and picks a word on the table phone: tap it, then confirm.",
            "Own agent: well done, keep guessing. The app does not count the guesses, so keep to the clue's number plus one yourselves.",
            "Bystander: the turn ends. The other team's agent: the turn ends and it counts for them.",
            "Assassin: the game ends at once and the other team wins.",
        )),
        RulesSection("⏭️", "Passing the turn", listOf(
            "A team can stop guessing at any time with Pass turn, on either phone. Then it is the other team's turn.",
        )),
        RulesSection("🏆", "Who wins", listOf(
            "The first team to uncover all its agents wins: 9 for the team that starts, 8 for the other.",
            "A team also wins if the other team uncovers its last agent.",
            "A team that picks the assassin loses straight away.",
        )),
        RulesSection("🔊", "Sounds and colours", listOf(
            "A chime for your own agent, a soft low sound for a bystander, a buzz for the other team's agent and a dramatic sound for the assassin.",
            "Red cards have stripes and blue cards have dots, so the colours can be told apart without seeing colour.",
        )),
    ),
)

val Portuguese = Strings(
    tagline = "Dois telemóveis. Um tabuleiro. Encontrem os vossos agentes.",
    spymasterTitle = "Telemóvel dos espiões",
    spymasterDesc = "Cria o jogo e mostra a chave secreta aos dois espiões.",
    tableTitle = "Telemóvel da mesa",
    tableDesc = "Mostra o tabuleiro a todos e recebe os palpites da equipa.",
    resumeGame = "Retomar jogo",
    twoPhonesHint = "São precisos dois telemóveis: um para os espiões e outro na mesa.",

    teamName = { if (it == Team.RED) "Vermelha" else "Azul" },
    teamLong = { if (it == Team.RED) "Equipa vermelha" else "Equipa azul" },
    turnOf = { if (it == Team.RED) "Vez da equipa vermelha" else "Vez da equipa azul" },
    agentsLabel = "agentes por encontrar",
    endTurn = "Passar a vez",
    newGame = "Novo jogo",
    newGameTitle = "Começar um jogo novo?",
    newGameText = "O jogo atual será perdido.",
    teamWins = { if (it == Team.RED) "A equipa vermelha ganha!" else "A equipa azul ganha!" },
    assassinHit = { if (it == Team.RED) "A equipa vermelha apanhou o assassino!" else "A equipa azul apanhou o assassino!" },

    keyHiddenTitle = "Chave escondida",
    keyHiddenHint = "Só para os espiões: toca para mostrar a chave. Volta a escondê-la antes que alguém espreite.",
    showKey = "Mostrar chave",
    hideKey = "Esconder chave",
    tableConnected = "Telemóvel da mesa ligado",
    tableWaiting = "À espera do telemóvel da mesa...",
    connectTitle = "Ligar o telemóvel da mesa",
    connectHelpNearby = "No outro telemóvel, toca em \"Telemóvel da mesa\". Este vai aparecer na lista.",
    connectHelpWifi = "Preferes usar o mesmo Wi-Fi? Escreve este endereço e este PIN no telemóvel da mesa.",
    wifiAddress = "Endereço",
    pinLabel = "PIN",
    pairTitle = { "Ligar a $it?" },
    pairText = { "Os dois telemóveis devem mostrar o código $it." },
    accept = "Aceitar",
    allow = "Permitir",
    reject = "Recusar",
    nearbyUnavailable = "A ligação Nearby não está disponível. O Wi-Fi continua a funcionar.",
    allowNearby = "Permite \"Dispositivos próximos\" para os dois telemóveis se encontrarem.",

    joinTitle = "Entrar num jogo",
    joinHint = "O telemóvel dos espiões tem de ter criado o jogo primeiro.",
    searching = "A procurar telemóveis de espiões por perto...",
    noneFound = "Ainda não foi encontrado nenhum.",
    connecting = "A ligar...",
    pairingCode = { "Código $it. Aceita no telemóvel dos espiões." },
    connectionFailed = "Não foi possível ligar. Tenta outra vez.",
    pinLocked = "Demasiados PIN errados. Espera um minuto e tenta outra vez.",
    wifiTitle = "Ou pelo mesmo Wi-Fi",
    addressHint = "Endereço, por ex. 192.168.1.20:8765",
    pinHint = "PIN",
    connect = "Ligar",
    permissionText = "Para encontrar o outro telemóvel, permite \"Dispositivos próximos\".",
    tapAWord = "Toca numa palavra para a escolher.",
    revealWord = { "Revelar \"$it\"" },
    connectionLost = "Ligação perdida. A tentar ligar de novo...",
    waitingNewGame = "À espera de um jogo novo...",

    leaveTitle = "Sair do jogo?",
    leave = "Sair",
    stay = "Ficar",
    cancel = "Cancelar",
    back = "Voltar",
    rulesButton = "Regras",
    rulesTitle = "Regras",
    rulesSections = listOf(
        RulesSection("🎯", "O objetivo", listOf(
            "Duas equipas, vermelha e azul. Cada equipa tem um espião que sabe quais são as palavras da sua equipa.",
            "Encontrem todos os agentes da vossa equipa antes que a outra encontre os seus, e fujam do assassino!",
        )),
        RulesSection("📱", "Os dois telemóveis", listOf(
            "O telemóvel dos espiões cria o jogo e mostra a chave secreta. Só os dois espiões a veem.",
            "O telemóvel da mesa fica no meio e mostra as 25 palavras a todos. Só descobre a cor de uma carta quando ela é escolhida.",
            "Os telemóveis falam entre si em direto, por Nearby (Bluetooth e Wi-Fi Direct) ou pelo mesmo Wi-Fi.",
        )),
        RulesSection("🗣️", "Dar uma pista", listOf(
            "Na sua vez, o espião diz em voz alta UMA palavra e UM número: quantas palavras do tabuleiro combinam com a pista.",
            "A pista não pode ser uma das palavras do tabuleiro. Nada de outras dicas, caras ou gestos.",
            "A app não recebe a pista. Só mostra de que equipa é a vez, por isso a pista diz-se em voz alta à mesa.",
        )),
        RulesSection("👆", "Adivinhar", listOf(
            "A equipa discute e escolhe uma palavra no telemóvel da mesa: toca nela e confirma.",
            "Agente da vossa equipa: boa, continuem. A app não conta os palpites, por isso respeitem vocês o número da pista mais um.",
            "Inocente: o turno acaba. Agente da outra equipa: o turno acaba e conta para eles.",
            "Assassino: o jogo acaba logo e a outra equipa ganha.",
        )),
        RulesSection("⏭️", "Passar a vez", listOf(
            "Uma equipa pode parar de adivinhar a qualquer momento com Passar a vez, em qualquer dos telemóveis. A vez passa à outra equipa.",
        )),
        RulesSection("🏆", "Quem ganha", listOf(
            "A primeira equipa a descobrir todos os seus agentes ganha: 9 para a que começa, 8 para a outra.",
            "Uma equipa também ganha se a outra descobrir o seu último agente.",
            "A equipa que escolher o assassino perde logo.",
        )),
        RulesSection("🔊", "Sons e cores", listOf(
            "Um toque agradável para um agente vosso, um som grave e suave para um inocente, um zumbido para um agente da outra equipa e um som dramático para o assassino.",
            "As cartas vermelhas têm riscas e as azuis têm pontos, para distinguir as equipas mesmo sem distinguir as cores.",
        )),
    ),
)

val Spanish = Strings(
    tagline = "Dos móviles. Un tablero. Encuentra a tus agentes.",
    spymasterTitle = "Móvil de los espías",
    spymasterDesc = "Crea la partida y muestra la clave secreta a los dos espías.",
    tableTitle = "Móvil de la mesa",
    tableDesc = "Muestra el tablero a todos y recibe las elecciones del equipo.",
    resumeGame = "Reanudar partida",
    twoPhonesHint = "Hacen falta dos móviles: uno para los espías y otro en la mesa.",

    teamName = { if (it == Team.RED) "Rojo" else "Azul" },
    teamLong = { if (it == Team.RED) "Equipo rojo" else "Equipo azul" },
    turnOf = { if (it == Team.RED) "Turno del equipo rojo" else "Turno del equipo azul" },
    agentsLabel = "agentes por encontrar",
    endTurn = "Pasar turno",
    newGame = "Nueva partida",
    newGameTitle = "¿Empezar una partida nueva?",
    newGameText = "Se perderá la partida actual.",
    teamWins = { if (it == Team.RED) "¡Gana el equipo rojo!" else "¡Gana el equipo azul!" },
    assassinHit = { if (it == Team.RED) "¡El equipo rojo eligió al asesino!" else "¡El equipo azul eligió al asesino!" },

    keyHiddenTitle = "Clave oculta",
    keyHiddenHint = "Solo para los espías: toca para mostrar la clave. Vuelve a ocultarla antes de que alguien mire.",
    showKey = "Mostrar clave",
    hideKey = "Ocultar clave",
    tableConnected = "Móvil de la mesa conectado",
    tableWaiting = "Esperando al móvil de la mesa...",
    connectTitle = "Conectar el móvil de la mesa",
    connectHelpNearby = "En el otro móvil, toca \"Móvil de la mesa\". Este aparecerá en la lista.",
    connectHelpWifi = "¿Prefieres el mismo Wi-Fi? Escribe esta dirección y este PIN en el móvil de la mesa.",
    wifiAddress = "Dirección",
    pinLabel = "PIN",
    pairTitle = { "¿Conectar con $it?" },
    pairText = { "Los dos móviles deben mostrar el código $it." },
    accept = "Aceptar",
    allow = "Permitir",
    reject = "Rechazar",
    nearbyUnavailable = "La conexión Nearby no está disponible. El Wi-Fi sigue funcionando.",
    allowNearby = "Permite \"Dispositivos cercanos\" para que los dos móviles se encuentren.",

    joinTitle = "Unirse a una partida",
    joinHint = "El móvil de los espías debe haber creado antes la partida.",
    searching = "Buscando móviles de espías cerca...",
    noneFound = "Todavía no se ha encontrado ninguno.",
    connecting = "Conectando...",
    pairingCode = { "Código $it. Acepta en el móvil de los espías." },
    connectionFailed = "No se pudo conectar. Inténtalo de nuevo.",
    pinLocked = "Demasiados PIN incorrectos. Espera un minuto e inténtalo de nuevo.",
    wifiTitle = "O por el mismo Wi-Fi",
    addressHint = "Dirección, p. ej. 192.168.1.20:8765",
    pinHint = "PIN",
    connect = "Conectar",
    permissionText = "Para encontrar el otro móvil, permite \"Dispositivos cercanos\".",
    tapAWord = "Toca una palabra para elegirla.",
    revealWord = { "Revelar \"$it\"" },
    connectionLost = "Conexión perdida. Reconectando...",
    waitingNewGame = "Esperando una partida nueva...",

    leaveTitle = "¿Salir de la partida?",
    leave = "Salir",
    stay = "Quedarse",
    cancel = "Cancelar",
    back = "Volver",
    rulesButton = "Reglas",
    rulesTitle = "Reglas",
    rulesSections = listOf(
        RulesSection("🎯", "El objetivo", listOf(
            "Dos equipos, rojo y azul. Cada equipo tiene un espía que sabe qué palabras son de su equipo.",
            "Encontrad a todos los agentes de vuestro equipo antes de que el otro encuentre a los suyos, ¡y huid del asesino!",
        )),
        RulesSection("📱", "Los dos móviles", listOf(
            "El móvil de los espías crea la partida y muestra la clave secreta. Solo los dos espías la ven.",
            "El móvil de la mesa se queda en el centro y muestra las 25 palabras a todos. Solo descubre el color de una carta cuando se elige.",
            "Los móviles se comunican en tiempo real, por Nearby (Bluetooth y Wi-Fi Direct) o por el mismo Wi-Fi.",
        )),
        RulesSection("🗣️", "Dar una pista", listOf(
            "En su turno, el espía dice en voz alta UNA palabra y UN número: cuántas palabras del tablero encajan con la pista.",
            "La pista no puede ser una de las palabras del tablero. Nada de otras pistas, caras ni gestos.",
            "La app no recibe la pista. Solo muestra de qué equipo es el turno, así que la pista se dice en voz alta en la mesa.",
        )),
        RulesSection("👆", "Adivinar", listOf(
            "El equipo lo comenta y elige una palabra en el móvil de la mesa: tócala y confirma.",
            "Agente de vuestro equipo: bien, seguid. La app no cuenta los intentos, así que respetad vosotros el número de la pista más uno.",
            "Inocente: el turno termina. Agente del otro equipo: el turno termina y cuenta para ellos.",
            "Asesino: la partida termina al instante y gana el otro equipo.",
        )),
        RulesSection("⏭️", "Pasar el turno", listOf(
            "Un equipo puede dejar de adivinar en cualquier momento con Pasar turno, en cualquiera de los móviles. Pasa el turno al otro equipo.",
        )),
        RulesSection("🏆", "Quién gana", listOf(
            "Gana el primer equipo que descubra a todos sus agentes: 9 el que empieza, 8 el otro.",
            "Un equipo también gana si el otro descubre a su último agente.",
            "El equipo que elige al asesino pierde de inmediato.",
        )),
        RulesSection("🔊", "Sonidos y colores", listOf(
            "Un tintineo para un agente propio, un sonido grave y suave para un inocente, un zumbido para un agente del otro equipo y un sonido dramático para el asesino.",
            "Las cartas rojas tienen rayas y las azules tienen puntos, para distinguir los colores sin necesidad de verlos.",
        )),
    ),
)
