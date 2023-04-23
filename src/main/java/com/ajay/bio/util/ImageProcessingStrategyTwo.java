package com.ajay.bio.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.common.collect.Sets;
import org.apache.commons.imaging.common.bytesource.ByteSourceFile;
import org.apache.commons.imaging.formats.tiff.TiffImageParser;

public class ImageProcessingStrategyTwo extends BaseStrategy {

    private static double dotLimit = 0;
    private static double backgroundLimit = 0;

    // we consider points with G > fLimit and boundary point G < bLimit
    private static int[] fLimit = {120, 100, 60, 50, 50, 45, 40, 30, 25};
    private static int[] bLimit = {100, 70, 40, 45, 38, 35, 30, 25, 18};

    public static int processTifFile(final Path inputFile, final Path outputDirPath) throws Exception {
        final File input = inputFile.toFile();
        final File output = new File(outputDirPath.toFile(), input.getName() + "-processed.png");

        final TiffImageParser imageParser = new TiffImageParser();
        final BufferedImage tiffImage = imageParser.getBufferedImage(new ByteSourceFile(input),
                                                                     imageParser.getDefaultParameters());
        boolean[][] isVisited = new boolean[tiffImage.getWidth()][tiffImage.getHeight()];

        int count = 0;
        final Set<Point> boundaryPoints = new HashSet<>();
        final Set<Point> islandPoints = new HashSet<>();

        //final Map<String, Long> timeMetricMap = new HashMap<>();
        for (int i = 0; i < fLimit.length; i++) {

            dotLimit = fLimit[i];
            backgroundLimit = bLimit[i];

            System.out.println("  => Pass - " + (i + 1) + " of " + fLimit.length + " passes");


            for (int y = 0; y < tiffImage.getHeight(); y++) {
                for (int x = 0; x < tiffImage.getWidth(); x++) {

                    if (!isVisited[x][y] && isPixelAboveThreshold(tiffImage.getRGB(x, y), dotLimit)) {

                        final long startTime = System.currentTimeMillis();
                        Set<Point> currentIslandPoints = expandIsland(x, y, isVisited, tiffImage);
//                        timeMetricMap.putIfAbsent("ExpandIsland", System.currentTimeMillis() - startTime);
//                        timeMetricMap.computeIfPresent("ExpandIsland",
//                                                       (key, value) -> value + (System.currentTimeMillis() - startTime));

                        final long startTime2 = System.currentTimeMillis();
                        if (!Sets.intersection(islandPoints, getBoundaryPoints(currentIslandPoints, tiffImage)).isEmpty()) {
//                            timeMetricMap.put("CollisionCheck", System.currentTimeMillis() - startTime);
//                            timeMetricMap.computeIfPresent("CollisionCheck",
//                                                           (key, value) -> value + (System.currentTimeMillis() - startTime2));
                            continue;
                        }
//                        timeMetricMap.put("CollisionCheck", System.currentTimeMillis() - startTime);
//                        timeMetricMap.computeIfPresent("CollisionCheck",
//                                                       (key, value) -> value + (System.currentTimeMillis() - startTime2));

                        if (currentIslandPoints.size() > 5000) {
                            continue;
                        }

                        if (currentIslandPoints.size() < 3) {
                            currentIslandPoints.forEach(p -> isVisited[p.x][p.y] = false);
                            continue;
                        }


                        final long startTime3 = System.currentTimeMillis();
                        final Set<Point> currentBoundaryPoints = getBoundaryToMark(currentIslandPoints, tiffImage);
//                        timeMetricMap.put("GetBoundaryToMark", System.currentTimeMillis() - startTime);
//                        timeMetricMap.computeIfPresent("GetBoundaryToMark",
//                                                       (key, value) -> value + (System.currentTimeMillis() - startTime3));

                        final long startTime4 = System.currentTimeMillis();
                        if (isValidBoundary(currentBoundaryPoints, tiffImage, currentIslandPoints)) {
                            boundaryPoints.addAll(currentBoundaryPoints);
                            islandPoints.addAll(currentIslandPoints);
                            islandPoints.addAll(getBoundaryPoints(currentIslandPoints, tiffImage));
                            ++count;

//                            timeMetricMap.put("IncreaseCount", System.currentTimeMillis() - startTime);
//                            timeMetricMap.computeIfPresent("IncreaseCount",
//                                                           (key, value) -> value + (System.currentTimeMillis() - startTime4));
                        } else {
                            //currentIslandPoints.forEach(p -> isVisited[p.x][p.y] = false);
//                            timeMetricMap.put("RejectIsland", System.currentTimeMillis() - startTime);
//                            timeMetricMap.computeIfPresent("RejectIsland",
//                                                           (key, value) -> value + (System.currentTimeMillis() - startTime4));
                        }
                    }
                }
            }

//            System.out.println("      - Count after pass - " + count);
//            timeMetricMap.forEach((name, value)-> System.out.println("      - " + name + " - "+ value/1000));
//
//            timeMetricMap.clear();
        }

        boundaryPoints.forEach(p -> tiffImage.setRGB(p.x, p.y, Color.ORANGE.getRGB()));

        writeCountOnImage(tiffImage, count);

        ImageIO.write(tiffImage, "png", output);

        //System.out.println(count);

        return count;
    }

    private static void writeCountOnImage(final BufferedImage tiffImage, final int count) {
        final Graphics graphics = tiffImage.getGraphics();
        graphics.setFont(graphics.getFont().deriveFont(30f));
        graphics.drawString(String.format("Total Count - %d", count), 100, 100);
        graphics.dispose();
    }

    private static boolean isValidBoundary(final Set<Point> boundaryPoints, final BufferedImage tiffImage, final Set<Point> islandPoints) {
        long matchCount = boundaryPoints.stream().filter(p -> isBackgroundPoint(tiffImage.getRGB(p.x, p.y))).count();
        long totalCount = boundaryPoints.size();

        double threshold = 0.80;
        return ((double) matchCount / totalCount) > threshold;
    }

    private static Set<Point> getBoundaryToMark(final Set<Point> pointList, final BufferedImage tiffImage) {
        final Set<Point> currentIsland = new HashSet<>(pointList);

        for (int i = 0; i < 3; i++) {
            final Set<Point> boundaryPoints = getBoundaryPoints(currentIsland, tiffImage);
            currentIsland.addAll(boundaryPoints);
        }

        return getBoundaryPoints(currentIsland, tiffImage);
    }

    private static Set<Point> getBoundaryPoints(final Set<Point> pointList, final BufferedImage tiffImage) {
        final Set<Point> boundaryPoints = new HashSet<>();
        pointList.forEach(p -> {
            final Set<Point> neighbours = getNeighbours(p, tiffImage.getWidth(), tiffImage.getHeight());
            boundaryPoints.addAll(neighbours);
        });

        boundaryPoints.removeAll(pointList);
        return boundaryPoints;
    }

    private static boolean isBackgroundPoint(final int rgb) {
        return getGrayScale(rgb) <= backgroundLimit;
    }

    private static Set<Point> expandIsland(final int x, final int y, final boolean[][] isVisited, final BufferedImage tiffImage) {
        final Deque<Point> stack = new ArrayDeque<>();
        stack.push(Point.of(x, y));

        final Set<Point> pointList = new HashSet<>();
        while (!stack.isEmpty()) {
            final Point current = stack.pop();
            isVisited[current.x][current.y] = true;

            // stack always has not-visited elements with threshold above DOT
            pointList.add(current);

            getNeighbours(current, tiffImage.getWidth(), tiffImage.getHeight())
                    .stream()
                    .filter(p -> !isVisited[p.x][p.y])
                    .filter(p -> isPixelAboveThreshold(tiffImage.getRGB(p.x, p.y), dotLimit))
                    .forEach(stack::push);
        }

        return pointList;
    }

    private static Set<Point> getNeighbours(final Point p, final int width, final int height) {
        return getNeighboursAtDistance(p, width, height, 1);
    }

    private static Set<Point> getNeighboursAtDistance(final Point p, final int width, final int height, final int distance) {
        final Set<Point> neighbours = new HashSet<>();

        // right
        if (p.x + distance < width) {
            neighbours.add(Point.of(p.x + distance, p.y));
        }

        // left
        if (p.x - distance >= 0) {
            neighbours.add(Point.of(p.x - distance, p.y));
        }

        // top
        if (p.y - distance >= 0) {
            neighbours.add(Point.of(p.x, p.y - distance));
        }

        // bottom
        if (p.y + distance < height) {
            neighbours.add(Point.of(p.x, p.y + distance));
        }

        // top-right
        if (p.y - distance >= 0 && p.x + distance < width) {
            neighbours.add(Point.of(p.x + distance, p.y - distance));
        }

        // top-left
        if (p.y - distance >= 0 && p.x - distance >= 0) {
            neighbours.add(Point.of(p.x - distance, p.y - distance));
        }

        // bottom-right
        if (p.y + distance < height && p.x + distance < width) {
            neighbours.add(Point.of(p.x + distance, p.y + distance));
        }

        // bottom-left
        if (p.y + distance < height && p.x - distance >= 0) {
            neighbours.add(Point.of(p.x - distance, p.y + distance));
        }

        return neighbours;
    }

}
