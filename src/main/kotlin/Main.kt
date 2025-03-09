
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.random.Random

// Data classes for Player, Property, and Cards
@Serializable
data class Player(
    var name: String,
    var position: Int = 0,
    var balance: Int = 1500,
    val properties: MutableList<Property> = mutableListOf(),
    var piece: String = ""
)

@Serializable
data class Property(val name: String, val cost: Int, val rent: Int, var owner: String? = null)

@Serializable
data class Card(val description: String, val action: String)

// Game pieces with name and corresponding image
val availablePieces = listOf(
    "Car",
    "Dog",
    "Hat",
    "Ship"
)

val players by lazy { mutableListOf<Player>() }
var currentPlayerIndex = 0


@Composable
fun PlayerSetupScreen(onStartGame: (List<Player>) -> Unit) {
    var player1Name by remember { mutableStateOf("Player 1") }
    var player2Name by remember { mutableStateOf("Player 2") }
    var player1Piece: String by remember { mutableStateOf(availablePieces[0]) }
    var player2Piece: String by remember { mutableStateOf(availablePieces[1]) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Player 1", style = MaterialTheme.typography.h5)
        TextField(
            value = player1Name,
            onValueChange = { player1Name = it },
            label = { Text("Player 1 Name") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("Choose your piece:")
        Row {
            availablePieces.forEach { pieceName ->
                Button(
                    onClick = { player1Piece = pieceName },
                    modifier = Modifier
                        .padding(4.dp)
                        .border(if (player1Piece == pieceName) 2.dp else 0.dp, Color.Black)
                ) {
                    Text(pieceName)
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Player 2", style = MaterialTheme.typography.h5)
        TextField(
            value = player2Name,
            onValueChange = { player2Name = it },
            label = { Text("Player 2 Name") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("Choose your piece:")
        Row {
            availablePieces.forEach { pieceName ->
                Button(
                    onClick = { player2Piece = pieceName },
                    modifier = Modifier
                        .padding(4.dp)
                        .border(if (player2Piece == pieceName) 2.dp else 0.dp, Color.Black)
                ) {
                    Text(pieceName)
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            val player1 = Player(player1Name, piece = player1Piece)
            val player2 = Player(player2Name, piece = player2Piece)
            onStartGame(listOf(player1, player2))
        }) {
            Text("Start Game")
        }
    }
}

@Composable
@Preview
fun MonopolyGameUI() {
    var gameStarted by remember { mutableStateOf(false) }
    var players by remember { mutableStateOf<List<Player>>(emptyList()) }
    val board = remember { createBoard() }

    if (!gameStarted) {
        PlayerSetupScreen { selectedPlayers ->
            players = selectedPlayers
            gameStarted = true
        }
    } else {
        var currentPlayerIndex by remember { mutableStateOf(0) }
        var currentPlayer by remember { mutableStateOf(players[currentPlayerIndex]) }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Monopoly Game", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(20.dp))

            Board(players, board)

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = {
                val diceRoll = Random.nextInt(1, 7) + Random.nextInt(1, 7)
                currentPlayer.position = (currentPlayer.position + diceRoll) % board.size

                // Handle landing on property
                val property = board[currentPlayer.position]
                if (property.cost > 0 && property.owner == null) {
                    // Player can buy the property
                    if (currentPlayer.balance >= property.cost) {
                        currentPlayer.balance -= property.cost
                        property.owner = currentPlayer.name
                        currentPlayer.properties.add(property)
                    }
                } else if (property.owner != null && property.owner != currentPlayer.name) {
                    // Player pays rent
                    currentPlayer.balance -= property.rent
                    players.find { it.name == property.owner }?.let { owner ->
                        owner.balance += property.rent
                    }
                }

                currentPlayerIndex = (currentPlayerIndex + 1) % players.size
                currentPlayer = players[currentPlayerIndex]
            }) {
                Text("Roll Dice 🎲")
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Player Position: ${currentPlayer.position} - ${board[currentPlayer.position].name}")
            Text("Player Balance: \$${currentPlayer.balance}")
        }
    }
}


@Composable
fun Board(players: List<Player>, board: List<Property>) {
    // Load the board image
    val boardImage = painterResource("board.png")

    Box(modifier = Modifier.size(800.dp, 800.dp)) {
        // Display the board image
        Image(
            painter = boardImage,
            contentDescription = "Monopoly Board",
            modifier = Modifier.fillMaxSize()
        )

        // Display player pieces
        val cellSize = 800.dp / 11 // Standard Monopoly board has 11 spaces per side
        players.forEach { player ->
            // Use player's piece name to find appropriate resource
            val pieceImageResource = "piece_${player.piece.lowercase()}.png"
            // Calculate position based on board layout
            // This is a simplified calculation assuming clockwise movement
            val position = player.position
            val offsetX: Int
            val offsetY: Int

            // Bottom row (0-10)
            if (position <= 10) {
                offsetX = 10 - position
                offsetY = 10
            }
            // Left column (11-20)
            else if (position <= 20) {
                offsetX = 0
                offsetY = 20 - position
            }
            // Top row (21-30)
            else if (position <= 30) {
                offsetX = position - 20
                offsetY = 0
            }
            // Right column (31-39)
            else {
                offsetX = 10
                offsetY = position - 30
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = (offsetX.dp * (cellSize / 1.dp)) + 10.dp, y = (offsetY.dp * (cellSize / 1.dp)) + 10.dp)
                    .background(
                        when (player.piece) {
                            "Car" -> Color.Red
                            "Dog" -> Color.Blue
                            "Hat" -> Color.Green
                            "Ship" -> Color.Yellow
                            else -> Color.Gray
                        }
                    )
            ) {
                Text(
                    text = player.name[0].toString(),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}


fun createBoard(): List<Property> {
    return listOf(
        Property("Go", 0, 0),
        Property("Mediterranean Avenue", 60, 2),
        Property("Baltic Avenue", 60, 4),
        Property("Oriental Avenue", 100, 6),
        Property("Chance", 0, 0),
        Property("Vermont Avenue", 100, 6),
        Property("Jail", 0, 0),
        Property("St. Charles Place", 140, 10),
        Property("States Avenue", 140, 10),
        Property("Virginia Avenue", 160, 12),
        Property("Community Chest", 0, 0),
        Property("St. James Place", 180, 14),
        Property("Tennessee Avenue", 180, 14),
        Property("New York Avenue", 200, 16),
        Property("Free Parking", 0, 0),
        Property("Kentucky Avenue", 220, 18),
        Property("Indiana Avenue", 220, 18),
        Property("Illinois Avenue", 240, 20),
        Property("Go to Jail", 0, 0),
        Property("Atlantic Avenue", 260, 22),
        Property("Ventnor Avenue", 260, 22),
        Property("Marvin Gardens", 280, 24),
        Property("Pacific Avenue", 300, 26),
        Property("North Carolina Avenue", 300, 26),
        Property("Pennsylvania Avenue", 320, 28),
        Property("Chance", 0, 0),
        Property("Park Place", 350, 35),
        Property("Boardwalk", 400, 50)
    )
}

fun startServer() {
    embeddedServer(Netty, port = 8080) {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("/game") {
                send(Json.encodeToString(players))
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val receivedPlayer = Json.decodeFromString<Player>(frame.readText())
                        players.find { it.name == receivedPlayer.name }?.apply {
                            position = receivedPlayer.position
                            balance = receivedPlayer.balance
                        }
                        send(Json.encodeToString(players))
                    }
                }
            }
        }
    }.start(wait = false)
}

fun main() {
    players.add(Player("Player 1"))
    players.add(Player("Player 2"))
    startServer()
    application {
        Window(onCloseRequest = ::exitApplication, title = "Monopoly Game") {
            MonopolyGameUI()
        }
    }
}
