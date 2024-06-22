package com.ajay.bio.util;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class BaseStrategy {
    private static final int MAX_BLOCK_SIZE_THRESHOLD = 5000;
    private static final int MIN_BLOCK_SIZE_THRESHOLD = 5;

    protected static boolean markDotRedIfAboveThreshold(final int i, final int j,
                                                      final BufferedImage tiffImage,
                                                      final boolean[][] pixels,
                                                      final int grayScaleThreshold) {
        final Stack<ImmutablePair<Integer, Integer>> stack = new Stack<>();

        stack.push(new ImmutablePair<>(i, j));

        final List<ImmutablePair<Integer, Integer>> pixelsToColor = new ArrayList<>();
        while (!stack.isEmpty()) {
            final ImmutablePair<Integer, Integer> current = stack.pop();
            int x = current.getKey();
            int y = current.getValue();

            pixels[x][y] = true;

            if (alreadyColored(tiffImage.getRGB(x, y))) {
                continue;
            }

            if (!isPixelAboveThreshold(tiffImage.getRGB(x, y), grayScaleThreshold)) {
                continue;
            }

            // tiffImage.setRGB(x, y, Color.RED.getRGB());
            pixelsToColor.add(new ImmutablePair<>(x, y));

            if (x + 1 < tiffImage.getWidth() && !pixels[x + 1][y]) {
                stack.push(new ImmutablePair<>(x + 1, y));
            }

            if (x - 1 >= 0 && !pixels[x - 1][y]) {
                stack.push(new ImmutablePair<>(x - 1, y));
            }

            if (y + 1 < tiffImage.getHeight() && !pixels[x][y + 1]) {
                stack.push(new ImmutablePair<>(x, y + 1));
            }

            if (y - 1 >= 0 && !pixels[x][y - 1]) {
                stack.push(new ImmutablePair<>(x, y - 1));
            }

            if (x + 1 < tiffImage.getWidth() && y + 1 < tiffImage.getHeight() && !pixels[x + 1][y + 1]) {
                stack.push(new ImmutablePair<>(x + 1, y + 1));
            }

            if (x - 1 >= 0 && y - 1 >= 0 && !pixels[x - 1][y - 1]) {
                stack.push(new ImmutablePair<>(x - 1, y - 1));
            }

            if (x - 1 >= 0 && y + 1 < tiffImage.getHeight() && !pixels[x - 1][y + 1]) {
                stack.push(new ImmutablePair<>(x - 1, y + 1));
            }

            if (x + 1 < tiffImage.getWidth() && y - 1 >= 0 && !pixels[x + 1][y - 1]) {
                stack.push(new ImmutablePair<>(x + 1, y - 1));
            }
        }

        if (isReallyBright(tiffImage, pixelsToColor, grayScaleThreshold)) {
            pixelsToColor.forEach(ImmutablePair -> tiffImage.setRGB(ImmutablePair.getKey(), ImmutablePair.getValue(), Color.RED.getRGB()));
            return true;
        }

        if (pixelsToColor.size() > MAX_BLOCK_SIZE_THRESHOLD) {
            pixelsToColor.forEach(ImmutablePair -> tiffImage.setRGB(ImmutablePair.getKey(), ImmutablePair.getValue(), Color.YELLOW.getRGB()));
            return false;
        }

        if (pixelsToColor.size() < MIN_BLOCK_SIZE_THRESHOLD) {
            //pixelsToColor.forEach(ImmutablePair -> tiffImage.setRGB(ImmutablePair.getKey(), ImmutablePair.getValue(), Color.PINK.getRGB()));
            return false;
        }

        final ImmutablePair<Integer, Integer> leftMostPoint = pixelsToColor.stream().reduce((first, second) -> {
            if (first.getKey() < second.getKey()) {
                return first;
            }

            return second;
        }).get();


        final ImmutablePair<Integer, Integer> bottomMostPoint = pixelsToColor.stream().reduce((first, second) -> {
            if (first.getValue() > second.getValue()) {
                return first;
            }

            return second;
        }).get();

        final ImmutablePair<Integer, Integer> topMostPoint = pixelsToColor.stream().reduce((first, second) -> {
            if (first.getValue() < second.getValue()) {
                return first;
            }

            return second;
        }).get();

        final ImmutablePair<Integer, Integer> rightMost = pixelsToColor.stream().reduce((first, second) -> {
            if (first.getKey() > second.getKey()) {
                return first;
            }

            return second;
        }).get();


        if (isDarkBackground(tiffImage, grayScaleThreshold, leftMostPoint, rightMost, bottomMostPoint, topMostPoint)) {
            pixelsToColor.forEach(ImmutablePair -> tiffImage.setRGB(ImmutablePair.getKey(), ImmutablePair.getValue(), Color.RED.getRGB()));
            return true;
        } else {
            pixelsToColor.forEach(ImmutablePair -> tiffImage.setRGB(ImmutablePair.getKey(), ImmutablePair.getValue(), Color.BLUE.getRGB()));
        }

        return false;
    }

    protected static boolean alreadyColored(final int rgb) {
        return rgb == Color.RED.getRGB() || rgb == Color.YELLOW.getRGB() || rgb == Color.BLUE.getRGB();
    }

    protected static boolean isReallyBright(final BufferedImage tiffImage, final List<ImmutablePair<Integer, Integer>> pixelsToColor,
                                    final int grayScaleThreshold) {
        for (ImmutablePair<Integer, Integer> point : pixelsToColor) {
            int rgb = tiffImage.getRGB(point.getKey(), point.getValue());
            if (getGrayScale(rgb) > grayScaleThreshold * 4) {
                return true;
            }
        }

        return false;
    }

    protected static boolean isDarkBackground(final BufferedImage tiffImage, final int grayScaleThreshold,
                                      final ImmutablePair<Integer, Integer> leftMostPoint, final ImmutablePair<Integer, Integer> rightMost, final ImmutablePair<Integer, Integer> bottomMostPoint, final ImmutablePair<Integer, Integer> topMostPoint) {
        boolean ans = true;
        int width = 5;
        if (leftMostPoint.getKey() - width >= 0) {
            ans = isPixelBelowThreshold(tiffImage.getRGB(leftMostPoint.getKey() - width, leftMostPoint.getValue()), grayScaleThreshold);
        }

        if (rightMost.getKey() + width < tiffImage.getWidth()) {
            ans = ans && isPixelBelowThreshold(tiffImage.getRGB(rightMost.getKey() + width, rightMost.getValue()), grayScaleThreshold);
        }

        if (topMostPoint.getValue() - width >= 0) {
            ans = ans && isPixelBelowThreshold(tiffImage.getRGB(topMostPoint.getKey(), topMostPoint.getValue() - width), grayScaleThreshold);
        }

        if (bottomMostPoint.getValue() + width < tiffImage.getHeight()) {
            ans = ans && isPixelBelowThreshold(tiffImage.getRGB(bottomMostPoint.getKey(), bottomMostPoint.getValue() + width), grayScaleThreshold);
        }

        return ans;
    }

    protected static boolean isPixelBelowThreshold(final int rgb, final int grayScaleThreshold) {
        if (alreadyColored(rgb)) {
            return true;
        }
        final Color color = new Color(rgb);
        int grayScale = (color.getBlue() + color.getRed() + color.getGreen()) / 3;
        return grayScale < grayScaleThreshold;
    }

    protected static boolean isPixelAboveThreshold(final int rgb, double grayScaleThreshold) {
        double grayScale = getGrayScale(rgb);
        return grayScale >= grayScaleThreshold;
    }

//    protected static double getGrayScale(final int rgb) {
//        final Color color = new Color(rgb);
//        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
//    }

    protected static double getGrayScale(final int rgb) {
        return new Color(rgb).getGreen();
    }

    protected static class Point {
        final int x;
        final int y;

        private Point(final int x, final int y) {
            this.x = x;
            this.y = y;
        }

        public static Point of(final int x, final int y) {
            return new Point(x,y);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "Point{" +
                           "x=" + x +
                           ", y=" + y +
                           '}';
        }
    }
}
