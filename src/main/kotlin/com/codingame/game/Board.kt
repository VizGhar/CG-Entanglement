package com.codingame.game

import com.codingame.gameengine.module.entities.GraphicEntityModule
import com.codingame.gameengine.module.entities.Group
import com.codingame.gameengine.module.entities.Sprite
import com.codingame.gameengine.module.entities.Text
import com.codingame.gameengine.module.toggle.ToggleModule
import kotlin.math.roundToInt


//  /.7.6.\
// /8.....5\
///9.......4\
//\10......3/
// \11....2/
//  \.0.1./


fun buildConnection(a: Int, b: Int) = Connection(minOf(a, b), maxOf(a, b))

data class Connection(val a: Int, val b: Int)

data class Piece(val connections: List<Connection>)

fun GraphicEntityModule.createRoad(connection: Connection) : Sprite {
    val sprite = createSprite()
    var (ca, cb) = connection
    val diff = cb - ca

    data class H(val a: Int, val h: Boolean, val v: Boolean)

    val (a, h, v) = when (ca) {
        0 -> H(diff + 0, false, false)
        1 -> H(12 - diff, true, false)
        2 -> H(diff + 11, false, false)
        3 -> H(34 - diff, false, true)
        4 -> H(diff + 22, false, false)
        5 -> H(23 - diff, false, true)
        6 -> H(diff, true, true)
        7 -> H(12 - diff, false, true)
        8 -> H(diff + 11, true, true)
        9 -> H(34 - diff, true, false)
        10 -> H(diff + 11, true, false)
        11 -> H(34 - diff, true, true)
        else -> throw IllegalStateException()
    }

    return sprite
        .setScaleX(if (h) -1.0 else 1.0)
        .setScaleY(if (v) -1.0 else 1.0)
        .setAnchor(0.5)
        .setY(75)
        .setX(75)
        .setImage("${a}.png")
        .setZIndex(10)
}

private const val baseX = 841
private const val baseY = 466
private const val xxDiff = 114
private const val xyDiff = -66
private const val yyDiff = 132

data class PieceSpecs(
    val group: Group,
    val scoreText: Text,
    val routes: Map<Connection, Sprite>
)

fun GraphicEntityModule.createPiece(
    piece: Piece,
    x: Int,
    y: Int
): PieceSpecs {
    val pieceGroup = createGroup()
        .setX(baseX + x * xxDiff + 0)
        .setY(baseY + x * xyDiff + y * yyDiff)
        .setAlpha(0.2)
    return PieceSpecs(
        group = pieceGroup,
        scoreText = createText().setFillColor(0xFFFFFF).setAnchorX(0.5).setAnchorY(0.5).setX(75).setY(75).setFontSize(60).setAlpha(0.0).setZIndex(20).setStrokeThickness(20.0).setStrokeColor(0x000000).also { pieceGroup.add(it) },
        routes = piece.connections.associateWith { connection -> createRoad(connection).also { pieceGroup.add(it) }
    })
}

fun GraphicEntityModule.createHelperPiece(x: Int, y: Int, toggleModule: ToggleModule) {
    val pieceGroup = createGroup()
        .setX(baseX + x * xxDiff + 0)
        .setY(baseY + x * xyDiff + y * yyDiff)
        .setZIndex(1)
    pieceGroup.add(createSprite().setImage("shadow.png"))
    pieceGroup.add(createText().setText("[$x, $y]").setFontSize(30).setAnchor(0.5).setX(75).setY(75).setFillColor(0xFFFFFF))
    toggleModule.displayOnToggleState(pieceGroup, "coordinates", true)
}