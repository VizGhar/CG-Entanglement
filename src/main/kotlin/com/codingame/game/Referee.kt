package com.codingame.game

import com.codingame.gameengine.core.AbstractPlayer
import com.codingame.gameengine.core.AbstractReferee
import com.codingame.gameengine.core.SoloGameManager
import com.codingame.gameengine.module.entities.GraphicEntityModule
import com.codingame.gameengine.module.entities.Sprite
import com.codingame.gameengine.module.entities.Text
import com.codingame.gameengine.module.toggle.ToggleModule
import com.google.inject.Inject
import kotlin.random.Random

private var oliveShadeId = 0
private var oliveShadeDir = -1
private val oliveShades = listOf(
    0x8FC91B, 0x8DC818, 0x8CC816, 0x8BC713, 0x8AC711, 0x88C60E, 0x87C60C, 0x86C509, 0x85C507, 0x83C404,
    0x82C402, 0x80C300, 0x7EBE00, 0x7AB800, 0x77B200, 0x73AC00, 0x6FA600, 0x6BA000, 0x679A00, 0x639400,
    0x5F8E00, 0x5B8800, 0x578200, 0x537C00, 0x4F7600, 0x4B7000, 0x476A00, 0x436400, 0x3F5E00, 0x3B5800,
    0x375200, 0x344C00, 0x324800, 0x304400, 0x2E4000, 0x2C3C00, 0x2A3800, 0x283400, 0x263000, 0x242C00,
    0x223800, 0x204000, 0x1E4000, 0x1C4000, 0x1A3C00, 0x183800, 0x163400, 0x143000, 0x123000, 0x103000
)

val nextOliveShade: Int get() {
    val result = oliveShades[oliveShadeId]
    if (oliveShadeId + oliveShadeDir !in 0..<oliveShades.size) oliveShadeDir *= -1
    oliveShadeId += oliveShadeDir
    return result
}

val middleCell = setOf(0 to 0)
val playableCells = setOf(
    0 to -3, 0 to -2, 0 to -1, 0 to 1, 0 to 2, 0 to 3,
    1 to -2, 1 to -1, 1 to 0, 1 to 1, 1 to 2, 1 to 3,
    2 to -1, 2 to 0, 2 to 1, 2 to 2, 2 to 3,
    3 to 0, 3 to 1, 3 to 2, 3 to 3,
    -1 to 2, -1 to 1, -1 to 0, -1 to -1, -1 to -2, -1 to -3,
    -2 to 1, -2 to 0, -2 to -1, -2 to -2, -2 to -3,
    -3 to 0, -3 to -1, -3 to -2, -3 to -3
)

class Referee : AbstractReferee() {

    private data class Position(val x: Int, val y: Int, val entrance: Int)

    @Inject
    private lateinit var gameManager: SoloGameManager<Player>

    @Inject
    private lateinit var graphicEntityModule: GraphicEntityModule

    @Inject
    private lateinit var toggleModule: ToggleModule

    private val random by lazy {
        Random(gameManager.testCaseInput[0].toLong())
    }

    override fun init() {
        gameManager.firstTurnMaxTime = 5000
        gameManager.turnMaxTime = 50
        gameManager.frameDuration = 1000
        gameManager.maxTurns = 200
        initBoard()
    }

    private var currentPosition = Position(0, -1, 0)

    override fun gameTurn(turn: Int) {
        placeTile()
        try {
            // execution
            gameManager.player.execute()

            if (gameManager.player.outputs.size != 1) {
                gameManager.loseGame("Invalid output"); return
            }

        } catch (_: AbstractPlayer.TimeoutException) {
            gameManager.loseGame("Timeout!")
            return
        }

        if (currentPosition.x to currentPosition.y !in playableCells) {
            gameManager.winGame("Congratulations. You made it to the end!")
        }
    }

    private data class Vector(val x: Int, val y: Int)
    private val exitToEntranceMapping = mapOf(0 to 7, 1 to 6, 2 to 9, 3 to 8, 4 to 11, 5 to 10, 6 to 1, 7 to 0, 8 to 3, 9 to 2, 10 to 5, 11 to 4)
    private val exitToDiffMapping = mapOf(0 to Vector(0, 1), 1 to Vector(0, 1), 2 to Vector(1, 1), 3 to Vector(1, 1), 4 to Vector(1, 0), 5 to Vector(1, 0), 6 to Vector(0, -1), 7 to Vector(0, -1), 8 to Vector(-1, -1), 9 to Vector(-1, -1), 10 to Vector(-1, 0), 11 to Vector(-1, 0))

    private val tiles = mutableMapOf<Vector, PieceSpecs>()
    private val pieces = mutableMapOf<Vector, Piece>()
    private val placedTiles = mutableSetOf<Vector>()

    private fun placeTile() {
        fun vector() = Vector(currentPosition.x, currentPosition.y)
        val pos = vector()
        placedTiles += pos

        val coloring = mutableListOf<Sprite>()
        val texts = mutableListOf<Text>()

        var scorePoints = 0
        while (placedTiles.contains(vector())) {
            val pos = vector()
            val piece = pieces[pos]!!
            val connection = piece.connections.first { it.a == currentPosition.entrance || it.b == currentPosition.entrance }
            val exit = if (connection.a == currentPosition.entrance) connection.b else connection.a

            coloring += tiles[pos]!!.routes[Connection(minOf(currentPosition.entrance, exit), maxOf(currentPosition.entrance, exit))]!!
            texts += tiles[pos]!!.scoreText

            currentPosition = Position(
                x = currentPosition.x + (exitToDiffMapping[exit]?.x ?: throw IllegalStateException()),
                y = currentPosition.y + (exitToDiffMapping[exit]?.y ?: throw IllegalStateException()),
                entrance = exitToEntranceMapping[exit] ?: throw IllegalStateException()
            )
        }

        val totalDuration = 500 + 600 * coloring.size
        val fractionPlacement = 500.0 / totalDuration
        val fractionColoring = 600.0 / totalDuration
        gameManager.frameDuration = totalDuration

        tiles[pos]?.group?.setAlpha(1.0)
        graphicEntityModule.commitEntityState(fractionPlacement, tiles[pos]?.group)

        coloring.forEachIndexed { i, c ->
            texts[i].setText("+${++scorePoints}")
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * i, texts[i], c)
            c.setTint(nextOliveShade)
            texts[i].setY(texts[i].y - 20).setAlpha(1.0)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.5), texts[i], c)
            texts[i].setY(texts[i].y - 20).setAlpha(0.0)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.95), texts[i])
            texts[i].setY(texts[i].y + 40).setAlpha(0.0)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 1), texts[i])
        }

    }

    private fun getRandomPiece(): Piece {
        val takenNums = mutableSetOf<Int>()
        fun rand(): Int {
            var a = random.nextInt(12)
            while (a in takenNums) { a = (a + 1) % 12 }
            takenNums += a
            return a
        }
        return Piece(List(6) {
            buildConnection(rand(), rand())
        })
    }

    private fun initBoard() {
        graphicEntityModule.createSprite().setImage("background.jpg")
        for (cell in playableCells + middleCell) {
            graphicEntityModule.createHelperPiece(cell.first, cell.second, toggleModule)
            val piece = getRandomPiece()
            tiles[Vector(cell.first, cell.second)] = graphicEntityModule.createPiece(piece, cell.first, cell.second)
            pieces[Vector(cell.first, cell.second)] = piece
        }
    }
}
