
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
data class Player(var name: String, var position: Int = 0, var balance: Int = 1500, val properties: MutableList<Property> = mutableListOf())

@Serializable
data class Property(val name: String, val cost: Int, val rent: Int, var owner: String? = null)

@Serializable
data class Card(val description: String, val effect: (Player) -> Unit)

val players = mutableListOf<Player>()
var currentPlayerIndex = 0

@Composable
@Preview
fun MonopolyGameUI() {
    var currentPlayer by remember { mutableStateOf(players.getOrNull(currentPlayerIndex) ?: Player("Player 1")) }
    val board = remember { createBoard() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Monopoly Game", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(20.dp))

        Board(currentPlayer, board)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            val diceRoll = Random.nextInt(1, 7) + Random.nextInt(1, 7)
            currentPlayer.position = (currentPlayer.position + diceRoll) % board.size

            // Handle landing on property
            val property = board[currentPlayer.position]
            if (property.cost > 0 && property.owner == null) {
                // Buy property
                if (currentPlayer.balance >= property.cost) {
                    currentPlayer.balance -= property.cost
                    property.owner = currentPlayer.name
                    currentPlayer.properties.add(property)
                }
            } else if (property.owner != null && property.owner != currentPlayer.name) {
                // Pay rent
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

@Composable
fun Board(player: Player, board: List<Property>) {
    Canvas(modifier = Modifier.size(800.dp, 200.dp).background(Color.LightGray)) {
        val boardSize = board.size
        val cellSize = size.width / boardSize

        for (i in board.indices) {
            drawRect(
                color = if (i == player.position) Color.Red else Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(i * cellSize, 0f),
                size = androidx.compose.ui.geometry.Size(cellSize, size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            )
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
