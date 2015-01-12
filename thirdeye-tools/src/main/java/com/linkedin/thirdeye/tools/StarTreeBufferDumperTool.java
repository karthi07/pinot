package com.linkedin.thirdeye.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.thirdeye.api.DimensionSpec;
import com.linkedin.thirdeye.api.MetricSpec;
import com.linkedin.thirdeye.api.StarTreeConfig;
import com.linkedin.thirdeye.api.StarTreeNode;
import com.linkedin.thirdeye.impl.StarTreePersistanceUtil;
import com.linkedin.thirdeye.impl.StarTreeUtils;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StarTreeBufferDumperTool {
  public static void main(String[] args) throws Exception {
    String configPath = args[0];
    String pathToTreeBinary = args[1];
    String dataDirectory = args[2];

    JsonNode jsonNode = new ObjectMapper().readTree(new FileInputStream(
            configPath));
    JsonNode timeBuckets = jsonNode.get("recordStoreFactoryConfig").get(
            "numTimeBuckets");
    StarTreeConfig starTreeConfig = StarTreeConfig.decode(new FileInputStream(configPath));

    StarTreeNode starTreeRootNode = StarTreePersistanceUtil
        .loadStarTree(new FileInputStream(pathToTreeBinary));

    List<StarTreeNode> leafNodes = new LinkedList<StarTreeNode>();
    StarTreeUtils.traverseAndGetLeafNodes(leafNodes, starTreeRootNode);

    List<DimensionSpec> dimensionNames = starTreeConfig.getDimensions();
    List<MetricSpec> metricSpecs = starTreeConfig.getMetrics();

    String[] dimValues = new String[dimensionNames.size()];
    int numDimensions = dimensionNames.size();
    int numMetrics = metricSpecs.size();
    int numTimeBuckets = timeBuckets.asInt();
    for (StarTreeNode node : leafNodes) {
      Map<String, Map<String, Integer>> forwardIndex = StarTreePersistanceUtil
          .readForwardIndex(node.getId().toString(), dataDirectory);
      Map<String, Map<Integer, String>> reverseIndex = StarTreeUtils
          .toReverseIndex(forwardIndex);

      Map<int[], Map<Long, int[]>> leafRecords = StarTreePersistanceUtil
          .readLeafRecords(dataDirectory, node.getId().toString(),
              numDimensions, numMetrics, numTimeBuckets);
      int[] emptyMetrics = new int[numMetrics];
      Arrays.fill(emptyMetrics, 0);
      String colSep = " | ", padding = "\t\t\t\t\t\t";
      for (Entry<int[], Map<Long, int[]>> entry : leafRecords.entrySet()) {
        StringBuilder sb = new StringBuilder();
        int[] dimArr = entry.getKey();
        for (int i = 0; i < numDimensions; i++) {
          String dimName = starTreeConfig.getDimensions().get(i).getName();
          dimValues[i] = reverseIndex.get(dimName).get(dimArr[i]);
        }
        sb.append(Arrays.toString(dimValues));
        Map<Long, int[]> timeSeries = entry.getValue();
        if (timeSeries.size() > 0) {
          sb.append("\n");

          for (Entry<Long, int[]> timeSeriesEntry : timeSeries.entrySet()) {
            int[] metrics = timeSeriesEntry.getValue();
            if (timeSeriesEntry.getKey() > 0
                && !Arrays.equals(emptyMetrics, metrics)) {
              sb.append(padding);
              sb.append(timeSeriesEntry.getKey());
              for (int i = 0; i < numMetrics; i++) {
                sb.append(colSep);
                sb.append(metrics[i]);
                sb.append("\t");
              }
              sb.append("\n");
            }
          }
        }
        System.out.println(sb);
      }
    }
  }
}
