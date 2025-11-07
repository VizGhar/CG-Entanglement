package com.codingame.game

import com.codingame.gameengine.core.AbstractPlayer
import com.codingame.gameengine.core.AbstractReferee
import com.codingame.gameengine.core.SoloGameManager
import com.codingame.gameengine.module.entities.GraphicEntityModule
import com.codingame.gameengine.module.entities.Text
import com.codingame.gameengine.module.toggle.ToggleModule
import com.google.inject.Inject
import kotlin.collections.plusAssign

private const val visitedColor = 0x8FC91B
private const val errorColor = 0xCC4C4C
private const val warningColor = 0xE6C84C
private const val willVisitColor = 0xFFFFFF
private const val wontVisitColor = 0x444444

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

private data class Vector(val x: Int, val y: Int)
private val exitToEntranceMapping = mapOf(0 to 7, 1 to 6, 2 to 9, 3 to 8, 4 to 11, 5 to 10, 6 to 1, 7 to 0, 8 to 3, 9 to 2, 10 to 5, 11 to 4)
private val exitToDiffMapping = mapOf(0 to Vector(0, 1), 1 to Vector(0, 1), 2 to Vector(1, 1), 3 to Vector(1, 1), 4 to Vector(1, 0), 5 to Vector(1, 0), 6 to Vector(0, -1), 7 to Vector(0, -1), 8 to Vector(-1, -1), 9 to Vector(-1, -1), 10 to Vector(-1, 0), 11 to Vector(-1, 0))

class Referee : AbstractReferee() {

    private data class Position(val x: Int, val y: Int, val exit: Int) {
        val target get() = Position(
            x = exitToDiffMapping[exit]!!.x + x,
            y = exitToDiffMapping[exit]!!.y + y,
            exit = exitToEntranceMapping[exit]!!
        )
    }

    private data class PathSegment(val x: Int, val y: Int, val a: Int, val b: Int) {
        init {
            if (x to y !in playableCells) throw IllegalArgumentException("Can't create path segment - OOP [$x,$y]")
            if (a >= b) throw IllegalArgumentException("Can't create path segment - invalid connections $a to $b")
            if (a !in 0..11 || b !in 0..11)  throw IllegalArgumentException("Can't create path segment - invalid connections $a to $b")
        }
    }

    private data class Path(val segments: List<PathSegment>)

    @Inject
    private lateinit var gameManager: SoloGameManager<Player>

    @Inject
    private lateinit var graphicEntityModule: GraphicEntityModule

    @Inject
    private lateinit var toggleModule: ToggleModule

    private val pieces by lazy {
        gameManager.testCaseInput.take(playableCells.size).associate {
            val s = it.split(" ").map { it.toInt() }
            Vector(s[0], s[1]) to Piece(s.drop(2).chunked(2).map { Connection(it[0], it[1]) })
        }
    }

    override fun init() {
        gameManager.firstTurnMaxTime = 1000
        gameManager.turnMaxTime = 50
        gameManager.frameDuration = 1000
        gameManager.maxTurns = playableCells.size
        initBoard()
    }

    private var currentPosition = Position(0, 0, 7)
    private var currentScore = 0

    override fun gameTurn(turn: Int) {
        val targetPosition = currentPosition.target
        val targetTile = pieces[Vector(targetPosition.x, targetPosition.y)]!!

        for (connection in targetTile.connections) {
            gameManager.player.sendInputLine("${connection.a} ${connection.b}")
        }

        try {
            gameManager.player.execute()

            if (gameManager.player.outputs.size != 2) { gameManager.loseGame("2 lines expected"); return }
            val line1 = gameManager.player.outputs[0].split(" ")
            val line2 = gameManager.player.outputs[1].split(" ")

            if (line1.size != 1 || line1[0].toIntOrNull() == null) { gameManager.loseGame("First line requires single integer - total score"); return }
            val outputScore = line1[0].toInt()
            if (line2.isEmpty() || line2.size % 4 != 0 || line2.any { it.toIntOrNull() == null }) { gameManager.loseGame("Second line requires quadruplets of space separated integers - x y entrance exit"); return }
            val outputPath = line2.map { it.toInt() }.chunked(4)

            val oobCells = outputPath.filter { (x, y) -> x to y !in playableCells }
            if (oobCells.isNotEmpty()) { gameManager.loseGame("Placing tile${if (oobCells.size == 1) "" else "s"} out of bounds:\n${oobCells.joinToString("\n"){ (x, y, a, b) -> "[$x, $y] $a=$b"}}"); return }
            val incorrectPathSegments1 = outputPath.filter { (_, _, a, b) -> a !in 0..11 || b !in 0..11 }
            if (incorrectPathSegments1.isNotEmpty()) { gameManager.loseGame("Incorrect path segment${if (incorrectPathSegments1.size == 1) "" else "s"} - entrances should be between 0 and 11:\n${incorrectPathSegments1.joinToString("\n") { (x, y, a, b) -> "[$x, $y] $a=$b" }}"); return }
            val incorrectPathSegments2 = outputPath.filter { (_, _, a, b) -> a == b }
            if (incorrectPathSegments2.isNotEmpty()) { gameManager.loseGame("Incorrect path segment${if (incorrectPathSegments2.size == 1) "" else "s"} - entrance and exit specified at the same position:\n${incorrectPathSegments2.joinToString("\n") { (x, y, a, b) -> "[$x, $y] $a=$b" }}"); return }

            val (expectedRoute, expectedScore) = expectedOutput()

            val outputPathNormalized = Path(outputPath.map { (x, y, a, b) -> PathSegment(x, y, minOf(a, b), maxOf(a, b)) })

            if (expectedRoute != outputPathNormalized) {
                showErrorRoute(outputPathNormalized, expectedRoute)
                gameManager.loseGame("Your path doesn't match:\n" +
                        "Expected:\n${expectedRoute.segments.joinToString("\n") { (x, y, a, b) -> "[$x, $y] $a=$b" }}\n" +
                        "Actual:\n${outputPathNormalized.segments.joinToString("\n") { (x, y, a, b) -> "[$x, $y] $a=$b" }}")
                return
            }

            placeTile(expectedRoute)

            currentScore += expectedScore
            if (currentScore != outputScore) {
                gameManager.loseGame("Incorrectly computed score")
                return
            }

            currentPosition = currentPosition.target

        } catch (_: AbstractPlayer.TimeoutException) {
            gameManager.loseGame("Timeout!")
            return
        }

        val nextPlace = currentPosition.target
        if (nextPlace.x to nextPlace.y !in playableCells) {
            gameManager.winGame("Congratulations. You made it to the end!")
        }
    }

    private fun showErrorRoute(
        outputPath: Path,
        expectedPath: Path
    ) {
        gameManager.frameDuration = 600

        val together = (outputPath.segments + expectedPath.segments).toSet()

        together.forEach { path ->
            val (x, y, a, b) = path
            val piece = Piece(listOf(Connection(minOf(a, b), maxOf(a, b))))
            val (g, _, r) = graphicEntityModule.createPiece(piece, x, y)
            val pathSegment = r.values.first()
            when {
                path in outputPath.segments && path in expectedPath.segments -> { pathSegment.setTint(warningColor); g.setZIndex(2)}
                path in outputPath.segments -> { pathSegment.setTint(errorColor); g.setZIndex(3) }
                else -> { pathSegment.setTint(willVisitColor); g.setZIndex(1) }
            }
            graphicEntityModule.commitEntityState(0.0, g.setAlpha(0.0), pathSegment)
            graphicEntityModule.commitEntityState(1.0, g.setAlpha(1.0), pathSegment)
        }
    }

    private val tiles = mutableMapOf<Vector, PieceSpecs>()
    private val placedTiles = mutableSetOf<Vector>()

    private fun expectedOutput() : Pair<Path, Int> {
        val result = mutableListOf<PathSegment>()
        currentPosition = currentPosition.target
        fun vector() = Vector(currentPosition.x, currentPosition.y)
        val pos = vector()
        placedTiles += pos
        var scorePoints = 0
        var increment = 1
        while (placedTiles.contains(vector())) {
            val pos = vector()
            val piece = pieces[pos]!!
            val connection = piece.connections.first { it.a == currentPosition.exit || it.b == currentPosition.exit }
            val exit = if (connection.a == currentPosition.exit) connection.b else connection.a

            currentPosition = Position(
                x = currentPosition.x + (exitToDiffMapping[exit]?.x ?: throw IllegalStateException()),
                y = currentPosition.y + (exitToDiffMapping[exit]?.y ?: throw IllegalStateException()),
                exit = exitToEntranceMapping[exit] ?: throw IllegalStateException()
            )
            val t = currentPosition.target

            val a = minOf(connection.a, connection.b)
            val b = maxOf(connection.a, connection.b)
            result += PathSegment(t.x, t.y, a, b)
            scorePoints += increment++
        }
        return Path(result) to scorePoints
    }

    private fun placeTile(route: Path) {
        val totalDuration = 500 + 600 * route.segments.size
        val fractionPlacement = 500.0 / totalDuration
        val fractionColoring = 600.0 / totalDuration
        gameManager.frameDuration = totalDuration

        var scorePoints = 0

        for ((i, pos) in route.segments.withIndex()) {
            val (x, y, a) = pos
            val pos = Vector(x, y)

            val connection = pieces[pos]?.connections?.first { it.a == a } ?: return

            val activeRoute = tiles[pos]!!.routes[connection]!!
            val coloring = activeRoute to tiles[pos]!!.routes.filter { it.value != activeRoute }.map { it.value }
            val texts = tiles[pos]!!.scoreText

            tiles[pos]?.group?.setAlpha(1.0)
            graphicEntityModule.commitEntityState(fractionPlacement, tiles[pos]?.group)

            val (c, r) = coloring
            texts.setText("+${++scorePoints}")
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * i, texts, c)
            c.setTint(visitedColor)
            r.forEach { r -> if (r.tint !in listOf(visitedColor, 0xFFFFFF)) r.setTint(0x333333) }
            texts.setY(texts.y - 20).setAlpha(1.0)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.5), texts, c, *r.toTypedArray())
            texts.setY(texts.y - 20).setAlpha(0.0)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.95), texts)
            texts.setY(texts.y + 40).setAlpha(0.0)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 1), texts)

            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.5), scoreText)
            scoreText.setScale(0.9)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.65), scoreText)
            scoreText.setScale(1.0).setText((scoreText.text.toInt() + scorePoints).toString())
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.7), scoreText)
            scoreText.setScale(0.9)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.75), scoreText)
            scoreText.setScale(0.75)
            graphicEntityModule.commitEntityState(fractionPlacement + fractionColoring * (i + 0.95), scoreText)
        }
    }

    lateinit var scoreText: Text

    private fun initBoard() {
        graphicEntityModule.createSprite().setImage("background.jpg")
        scoreText = graphicEntityModule.createText().setText("0").setFillColor(0xFFFFFF).setX(250).setY(250).setFontSize(200).setScale(0.75).setAnchor(0.5).setStrokeThickness(15.0).setStrokeColor(0x000000)
        graphicEntityModule.createText().setText("Score").setFillColor(0xFFFFFF).setX(250).setY(100).setFontSize(75).setAnchor(0.5).setStrokeThickness(10.0).setStrokeColor(0x000000)

        for (cell in playableCells + middleCell) {
            graphicEntityModule.createHelperPiece(cell.first, cell.second, toggleModule)
            if (cell !in middleCell) {
                val piece = pieces[Vector(cell.first, cell.second)] ?: throw java.lang.IllegalStateException()
                tiles[Vector(cell.first, cell.second)] = graphicEntityModule.createPiece(piece, cell.first, cell.second).also {
                    it.routes.values.forEach { it.setTint(wontVisitColor) }
                }
            }
        }

        // precompute route
        var currentPosition = Position(0, -1, 0)
        fun vector() = Vector(currentPosition.x, currentPosition.y)

        while (currentPosition.x to currentPosition.y in playableCells) {
            val pos = vector()
            val piece = pieces[pos]!!

            val connection = piece.connections.first { it.a == currentPosition.exit || it.b == currentPosition.exit }
            val exit = if (connection.a == currentPosition.exit) connection.b else connection.a

            tiles[pos]!!.routes[Connection(minOf(currentPosition.exit, exit), maxOf(currentPosition.exit, exit))]!!.setTint(willVisitColor)

            tiles[pos]!!.routes[Connection(minOf(currentPosition.exit, exit), maxOf(currentPosition.exit, exit))]!!

            currentPosition = Position(
                x = currentPosition.x + (exitToDiffMapping[exit]?.x ?: throw IllegalStateException()),
                y = currentPosition.y + (exitToDiffMapping[exit]?.y ?: throw IllegalStateException()),
                exit = exitToEntranceMapping[exit] ?: throw IllegalStateException()
            )
        }
    }
}
