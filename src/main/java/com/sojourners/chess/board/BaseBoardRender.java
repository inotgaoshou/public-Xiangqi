package com.sojourners.chess.board;

import com.sojourners.chess.util.MathUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;


public abstract class BaseBoardRender implements BoardRender {

    private Canvas canvas;

    GraphicsContext gc;

    private static int autoPieceSize;

    public BaseBoardRender(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
    }

    void paint(ChessBoard.BoardSize boardSize, char[][] board, ChessBoard.Step prevStep, ChessBoard.Point remark,
               boolean stepTip, List<ChessBoard.Step> pvSteps, boolean isReverse, boolean showNumber){
        int padding = getPadding(boardSize);
        int piece = getPieceSize(boardSize);
        int pos = padding + piece / 2;

        canvas.setWidth(2 * padding + piece * 9);
        canvas.setHeight(2 * padding + piece * 10);

        // 绘制背景图片
        drawBackgroundImage(canvas.getWidth(), canvas.getHeight());
        // 绘制棋盘线
        drawBoardLine(pos, padding, piece, isReverse, boardSize);
        // 绘制线路序号
        if (showNumber) {
            drawBoardNum(pos, piece, isReverse, boardSize);
        }
        // 绘制楚河汉界
        drawCenterText(pos, piece, boardSize);
        // 上一步走棋记号
        if (prevStep != null) {
            drawStepRemark(pos, piece, prevStep.first.x, prevStep.first.y, true, isReverse, boardSize);
            drawStepRemark(pos, piece, prevStep.second.x, prevStep.second.y, true, isReverse, boardSize);
        }
        // 已选择棋子记号
        if (remark != null) {
            drawStepRemark(pos, piece, remark.x, remark.y, false, isReverse, boardSize);
        }
        // 绘制棋子
        drawPieces(pos, piece, board, isReverse, boardSize);

        // 绘制棋步提示 - 修改部分
        if (stepTip && pvSteps != null && !pvSteps.isEmpty()) {
            final int MAX_PV_DISPLAY = 5; // 最多显示5个PV

            System.out.println("PV Steps count: " + pvSteps.size());
            for (int i = 0; i < pvSteps.size(); i++) {
                ChessBoard.Step step = pvSteps.get(i);
                System.out.println("PV" + (i+1) + ": (" + step.first.x + "," + step.first.y + ") -> (" + step.second.x + "," + step.second.y + ")");
            }

            for (int pvIndex = 0; pvIndex < Math.min(pvSteps.size(), MAX_PV_DISPLAY); pvIndex++) {
                ChessBoard.Step step = pvSteps.get(pvIndex);
                if (step != null) {
                    // 只有主PV(索引0)的第一步设置为isFirst=true
                    boolean isFirst = (pvIndex == 0);
                    drawStepTips(pos, piece, step.first.x, step.first.y,
                            step.second.x, step.second.y,
                            isReverse, pvIndex, isFirst);
                }
            }
        }
    }

    public void paint(ChessBoard.BoardSize boardSize, char[][] board, ChessBoard.Step prevStep, ChessBoard.Point remark,
                      boolean stepTip, ChessBoard.Step tipFirst, ChessBoard.Step tipSecond, boolean isReverse, boolean showNumber) {

        // 转换为新的多PV格式
        List<ChessBoard.Step> pvSteps = new ArrayList<>();
        if (tipFirst != null) pvSteps.add(tipFirst);
        if (tipSecond != null) pvSteps.add(tipSecond);

        // 调用新方法
        paint(boardSize, board, prevStep, remark, stepTip, pvSteps, isReverse, showNumber);
    }

    // paint edit chess board demo piece
    public void paintDemoBoard(ChessBoard.BoardSize boardSize, char[][] board, ChessBoard.Point remark) {
        int piece = getPieceSize(boardSize);
        int padding = getPadding(boardSize);
        int pos = padding + piece / 2;

        canvas.setWidth(2 * padding + piece * 2);
        canvas.setHeight(2 * padding + piece * 10);

        // 绘制背景
        gc.setFill(getBackgroundColor());
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        // 已选择棋子记号
        if (remark != null) {
            drawStepRemark(pos, piece, remark.x, remark.y, true, false, boardSize);
        }
        // 绘制棋子
        drawPieces(pos, piece, board, false, boardSize);

    }

    @Override
    public void drawCenterText(int pos, int piece, ChessBoard.BoardSize style) {
        // 绘制楚河汉界
        double centerTextSize = getCenterTextSize(style);
        gc.setFont(Font.font(centerTextSize));
        gc.setFill(Color.BLACK);
        gc.setGlobalAlpha(0.55);
        gc.fillText("楚", pos + 2 * piece - centerTextSize, pos + 4.5 * piece + centerTextSize / 3.6);
        gc.fillText("河", pos + 3 * piece - centerTextSize, pos + 4.5 * piece + centerTextSize / 3.6);
        gc.fillText("汉", pos + 5 * piece, pos + 4.5 * piece + centerTextSize / 3.6);
        gc.fillText("界", pos + 6 * piece, pos + 4.5 * piece + centerTextSize / 3.6);
        gc.setGlobalAlpha(1);
    }

    /**
     * 获取楚河汉界字体大小
     * @return
     */
    private double getCenterTextSize(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 2.5d;
    }

    public void drawStepTips(int pos, int piece, int x1, int y1, int x2, int y2, boolean isReverse, int pvIndex, boolean isFirst) {
        x1 = pos + piece * getReverseX(x1, isReverse);
        y1 = pos + piece * getReverseY(y1, isReverse);
        x2 = pos + piece * getReverseX(x2, isReverse);
        y2 = pos + piece * getReverseY(y2, isReverse);

        gc.save();

        try {
            double angle = MathUtils.calculateAngle(x1, y1, x2, y2);
            Rotate r = new Rotate(angle, x1, y1);
            gc.setTransform(r.getMxx(), r.getMyx(), r.getMxy(), r.getMyy(), r.getTx(), r.getTy());

            // 使用更鲜艳的颜色
            Color[] pvColors = {
                    Color.rgb(255, 50, 50, 0.9),    // PV0 - 鲜红色
                    Color.rgb(0, 100, 255, 0.9),    // PV1 - 亮蓝色
                    Color.rgb(0, 200, 0, 0.9),      // PV2 - 亮绿色
                    Color.rgb(255, 165, 0, 0.9),    // PV3 - 橙色
                    Color.rgb(180, 0, 220, 0.9)     // PV4 - 亮紫色
            };

            // 增加线宽
            double[] lineWidths = {5.0, 4.0, 3.5, 3.0, 2.5};

            Color color = pvColors[Math.min(pvIndex, pvColors.length - 1)];
            double lineWidth = lineWidths[Math.min(pvIndex, lineWidths.length - 1)];

            gc.setFill(color);
            gc.setStroke(color);
            gc.setLineWidth(lineWidth);

            int len = (int) MathUtils.calculateDistance(x1, y1, x2, y2);
            x2 = x1 - len;

            // 基本参数
            double offY = piece / 10.0;  // 增大箭头
            double offX = piece / 4.0;
            double h = piece / 5.0;      // 增大箭头高度

            // 简单的垂直偏移
            double pvOffsetY = pvIndex * piece / 6.0;  // 增大间距
            double currentY1 = y1 + pvOffsetY;

            // 绘制箭头多边形
            gc.fillPolygon(new double[]{x1, x2 + offX, x2 + offX + h / 2, x2, x2 + offX + h / 2, x2 + offX, x1},
                    new double[]{currentY1 - offY, currentY1 - offY, currentY1 - offY - h, currentY1, currentY1 + offY + h, currentY1 + offY, currentY1 + offY},
                    7);

            // 方案：使用大号彩色标签框
            if (pvIndex < 5) {
                double boxWidth = piece / 3.0;    // 增大标签框
                double boxHeight = piece / 4.0;
                double boxX = x1 - piece / 3.0;
                double boxY = currentY1 - boxHeight / 2;

                // 绘制背景框 - 使用对比色
                gc.setFill(Color.rgb(255, 255, 255, 0.9));  // 白色背景
                gc.fillRect(boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2);

                gc.setFill(color);
                gc.fillRect(boxX, boxY, boxWidth, boxHeight);

                // 绘制黑色粗体数字
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", FontWeight.BLACK, piece / 4.0));  // 大号字体
                String number = String.valueOf(pvIndex + 1);

                // 计算文字居中位置
                double textX = boxX + boxWidth / 2 - (number.length() * piece / 12.0);
                double textY = boxY + boxHeight * 0.7;

                gc.fillText(number, textX, textY);
            }

        } finally {
            gc.restore();
        }
    }
    int getReverseY(int y, boolean isReverse) {
        return isReverse ? (9 - y) : y;
    }

    int getReverseX(int x, boolean isReverse) {
        return isReverse ? (8 - x) : x;
    }

    /**
     * 棋步标识矩形线条宽度
     * @return
     */
    private double getStepRectWitdh(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 25d;
    }

    @Override
    public void drawStepRemark(int pos, int piece, int x, int y, boolean isPrevStep, boolean isReverse, ChessBoard.BoardSize style) {
        x = pos + piece * getReverseX(x, isReverse);
        y = pos + piece * getReverseY(y, isReverse);

        double len = piece / 1.08;
        gc.setLineWidth(getStepRectWitdh(style));
        Color color = isPrevStep ? Color.web("#bf242a") : Color.web("#0000FF");
        gc.setStroke(color);
        gc.strokePolyline(new double[]{x - len / 2 + len / 6, x - len / 2, x - len / 2},
                new double[]{y - len / 2, y - len / 2, y - len / 2 + len / 6},
                3);
        gc.strokePolyline(new double[]{x - len / 2 + len / 6, x - len / 2, x - len / 2},
                new double[]{y + len / 2, y + len / 2, y + len / 2 - len / 6},
                3);
        gc.strokePolyline(new double[]{x + len / 2 - len / 6, x + len / 2, x + len / 2},
                new double[]{y - len / 2, y - len / 2, y - len / 2 + len / 6},
                3);
        gc.strokePolyline(new double[]{x + len / 2 - len / 6, x + len / 2, x + len / 2},
                new double[]{y + len / 2, y + len / 2, y + len / 2 - len / 6},
                3);
    }

    @Override
    public void drawBoardLine(int pos, int padding, int piece, boolean isReverse, ChessBoard.BoardSize style) {
        // 棋盘竖线横线
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(getOutRectWidth(style));
        gc.setGlobalAlpha(0.75);
        gc.strokeRect(pos - padding / 2, pos - padding / 2, piece * 8 + padding, piece * 9 + padding);
        gc.setGlobalAlpha(1);
        gc.setLineWidth(getInnerRectWidth(style));
        gc.strokeRect(pos, pos, piece * 8, piece * 9);
        for (int i = 1; i < 9; i++) {
            gc.strokeLine(pos, pos + piece * i, pos + piece * 8, pos + piece * i);
        }
        for (int i = 1; i < 8; i++) {
            gc.strokeLine(pos + piece * i, pos, pos + piece * i, pos + piece * 4);
            gc.strokeLine(pos + piece * i, pos + piece * 5, pos + piece * i, pos + piece * 9);
        }
        // 九宫斜线
        gc.strokeLine(pos + piece * 3, pos, pos + piece * 5, pos + piece * 2);
        gc.strokeLine(pos + piece * 3, pos + piece * 2, pos + piece * 5, pos);
        gc.strokeLine(pos + piece * 3, pos + piece * 9, pos + piece * 5, pos + piece * 7);
        gc.strokeLine(pos + piece * 3, pos + piece * 7, pos + piece * 5, pos + piece * 9);
        // 炮兵位置记号
        for (int i = 0; i < 9; i += 2) {
            String style1 = i == 0 ? "r" : (i == 8 ? "l" : "lr");
            drawStarPos(pos + piece * i, pos + piece * 3, piece, style1);
            drawStarPos(pos + piece * i, pos + piece * 6, piece, style1);
        }
        for (int i = 1; i < 9; i += 6) {
            drawStarPos(pos + piece * i, pos + piece * 2, piece, "lr");
            drawStarPos(pos + piece * i, pos + piece * 7, piece, "lr");
        }
    }

    public void drawBoardNum(int pos, int piece, boolean isReverse, ChessBoard.BoardSize style) {
        // 绘制线路序号
        double numberSize = getNumberSize(style);
        gc.setFont(Font.font(numberSize));
        gc.setFill(Color.BLACK);
        for (int i = 0; i < 9; i++) {
            // 黑方
            char number = (char) ('１' + i);
            double xTop = pos + i * piece - numberSize / 2, xBottom = pos + (8 - i) * piece - numberSize / 2;
            double yTop = pos - piece / 4, yBottom = pos + 9 * piece + piece / 2.3;
            gc.fillText(String.valueOf(number), isReverse ? xBottom : xTop, isReverse ? yBottom : yTop);
            // 红方
            gc.fillText(ChessBoard.map.get(number), isReverse ? xTop : xBottom, isReverse ? yTop : yBottom);
        }
    }

    private void drawStarPos(int x, int y, int w, String style) {
        int offset = w / 16;
        int len = w / 6;
        if (style.contains("l")) {
            gc.strokePolyline(new double[]{x - offset - len, x - offset, x - offset},
                    new double[]{y - offset, y - offset, y - offset - len}, 3);

            gc.strokePolyline(new double[]{x - offset - len, x - offset, x - offset},
                    new double[]{y + offset, y + offset, y + offset + len}, 3);


        }
        if (style.contains("r")) {
            gc.strokePolyline(new double[]{x + offset + len, x + offset, x + offset},
                    new double[]{y - offset, y - offset, y - offset - len}, 3);

            gc.strokePolyline(new double[]{x + offset + len, x + offset, x + offset},
                    new double[]{y + offset, y + offset, y + offset + len}, 3);

        }
    }

    /**
     * 获取线路序号字体大小
     * @return
     */
    private double getNumberSize(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 4d;
    }

    /**
     * 棋盘内矩形线条宽度
     * @return
     */
    private double getInnerRectWidth(ChessBoard.BoardSize style) {
        return getOutRectWidth(style) / 2d;
    }

    /**
     * 棋盘外矩形线条宽度
     * @return
     */
    private double getOutRectWidth(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 40d;
    }

    /**
     * 棋子大小
     * @return
     */
    public int getPieceSize(ChessBoard.BoardSize style) {
        switch (style) {
            case LARGE_BOARD: {
                return 120;
            }
            case BIG_BOARD: {
                return 72;
            }
            case MIDDLE_BOARD: {
                return 64;
            }
            case SMALL_BOARD: {
                return 48;
            }
            case AUTOFIT_BOARD: {
                return autoPieceSize;
            }
            default: {
                return 64;
            }
        }
    }

    /**
     * 棋盘边距
     * @return
     */
    public int getPadding(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 6;
    }

    public void setAutoPieceSize(int size) {
        this.autoPieceSize = size;
    }
}
