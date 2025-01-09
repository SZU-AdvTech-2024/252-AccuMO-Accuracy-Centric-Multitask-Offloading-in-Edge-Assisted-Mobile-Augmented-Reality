package com.example.accumo.sched;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MpcScheduler extends Scheduler {
    private final int lookahead = 30;
    private final double discount = 1;
    private final List<List<Integer>> trails;

    public MpcScheduler() {
        this.trails = generateTrails(Collections.emptyList());
    }

    private List<List<Integer>> generateTrails(List<Integer> prefix) {
        List<List<Integer>> ret = new ArrayList<>();
        for (int i = 0; i < tasks.length; i++) {
            List<Integer> t = new ArrayList<>(prefix);
            t.add(i);
            t.addAll(Collections.nCopies(tasks[i].latency - 1, -1));
            if (t.size() < lookahead) {
                ret.addAll(generateTrails(t));
            } else {
                ret.add(t.subList(0, lookahead));
            }
        }
        return ret;
    }

    @Override
    public int schedule(int frame, double... accDropRates) {
        int minTask = -1;
        double minDrop = Double.MAX_VALUE;

        for (List<Integer> trail : trails) {
            double drop = calculatePathCost(frame, minDrop, trail, accDropRates);
            if (drop < minDrop) {
                minDrop = drop;
                minTask = trail.get(0);
            }
        }

        if (minTask == -1) {
            minTask = (tasks[0].latestOffload < tasks[1].latestOffload) ? 0 : 1;
        }
        return minTask;
    }

    private double calculatePathCost(int frame, double minDrop, List<Integer> trail, double[] accDropRates) {
        Task[] taskCopy = copy();
        double drop = 0;
        double discountFactor = 1;

        for (int i = 0; i < trail.size(); i++) {
            int currentFrame = frame + i;
            for (int j = 0; j < taskCopy.length; j++) {
                if (taskCopy[j].pendingOffload >= 0 && currentFrame - taskCopy[j].pendingOffload >= taskCopy[j].latency) {
                    taskCopy[j].finishOffload();
                }
                if (currentFrame - taskCopy[j].latestOffload > taskCopy[j].strideLimit) {
                    return Double.MAX_VALUE;
                }
                if (trail.get(i) == j) {
                    taskCopy[j].offload(currentFrame);
                }
                int stride = (taskCopy[j].latestOffload >= 0) ? currentFrame - taskCopy[j].latestOffload : Integer.MAX_VALUE;
                drop += discountFactor * Math.min(accDropRates[j] * stride, taskCopy[j].accDropLimit);
            }
            discountFactor *= discount;
            if (drop > minDrop) {
                break; // 提前剪枝
            }
        }
        return drop;
    }
}