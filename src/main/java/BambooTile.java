import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义 Canvas，用于绘制 100x140 px 的条子麻将牌。
 */
public class BambooTile extends Canvas {
    private static final double TILE_WIDTH = 100;
    private static final double TILE_HEIGHT = 140;
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color BORDER_COLOR = Color.BLACK;
    private static final Color STICK_COLOR = Color.web("#1c8b4d");
    private static final Color NODE_COLOR = Color.web("#f4e5b8");

    private final int value;

    public BambooTile(int value) {
        super(TILE_WIDTH, TILE_HEIGHT);
        if (value < 1 || value > 9) {
            throw new IllegalArgumentException("Bamboo tile value must be between 1 and 9.");
        }
        this.value = value;
        draw();
    }

    public int getValue() {
        return value;
    }

    private void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, TILE_WIDTH, TILE_HEIGHT);
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(3);
        gc.strokeRect(1.5, 1.5, TILE_WIDTH - 3, TILE_HEIGHT - 3);

        double stickWidth = TILE_WIDTH * 0.16;
        double stickHeight = TILE_HEIGHT * 0.6;
        double nodeDiameter = stickWidth * 0.8;

        for (Point2D center : stickCenters(value)) {
            drawStick(gc, center, stickWidth, stickHeight, nodeDiameter);
        }
    }

    private void drawStick(GraphicsContext gc, Point2D center, double stickWidth,
                           double stickHeight, double nodeDiameter) {
        double cx = center.getX() * TILE_WIDTH;
        double cy = center.getY() * TILE_HEIGHT;
        double left = cx - stickWidth / 2;
        double top = cy - stickHeight / 2;

        gc.setFill(STICK_COLOR);
        gc.fillRoundRect(left, top, stickWidth, stickHeight, stickWidth, stickWidth);

        double node1Y = top + stickHeight * 0.25 - nodeDiameter / 2;
        double node2Y = top + stickHeight * 0.75 - nodeDiameter / 2;
        double nodeX = cx - nodeDiameter / 2;

        gc.setFill(NODE_COLOR);
        gc.fillOval(nodeX, node1Y, nodeDiameter, nodeDiameter);
        gc.fillOval(nodeX, node2Y, nodeDiameter, nodeDiameter);
        gc.setStroke(STICK_COLOR.darker());
        gc.setLineWidth(1);
        gc.strokeOval(nodeX, node1Y, nodeDiameter, nodeDiameter);
        gc.strokeOval(nodeX, node2Y, nodeDiameter, nodeDiameter);
    }

    /**
     * 预设不同数字的条子中心点，模仿骰子点阵排布。
     */
    private List<Point2D> stickCenters(int value) {
        double left = 0.28;
        double right = 0.72;
        double centerX = 0.5;
        double top = 0.28;
        double middle = 0.5;
        double bottom = 0.72;

        List<Point2D> points = new ArrayList<>();
        switch (value) {
            case 1:
                points.add(new Point2D(centerX, middle));
                break;
            case 2:
                points.add(new Point2D(left, top));
                points.add(new Point2D(right, bottom));
                break;
            case 3:
                points.add(new Point2D(left, top));
                points.add(new Point2D(centerX, middle));
                points.add(new Point2D(right, bottom));
                break;
            case 4:
                points.add(new Point2D(left, top));
                points.add(new Point2D(right, top));
                points.add(new Point2D(left, bottom));
                points.add(new Point2D(right, bottom));
                break;
            case 5:
                points.addAll(stickCenters(4));
                points.add(new Point2D(centerX, middle));
                break;
            case 6:
                points.addAll(stickCenters(4));
                points.add(new Point2D(left, middle));
                points.add(new Point2D(right, middle));
                break;
            case 7:
                points.addAll(stickCenters(6));
                points.add(new Point2D(centerX, top));
                break;
            case 8:
                points.addAll(stickCenters(7));
                points.add(new Point2D(centerX, bottom));
                break;
            case 9:
                points.addAll(stickCenters(8));
                points.add(new Point2D(centerX, middle));
                break;
            default:
                throw new IllegalArgumentException("Unsupported value " + value);
        }
        return points;
    }
}
