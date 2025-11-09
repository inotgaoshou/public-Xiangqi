package com.sojourners.chess.enginee;


import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.StringUtils;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 引擎封装
 */
public class Engine {

    private Process process;

    private String protocol;

    private AnalysisModel analysisModel;
    private long analysisValue;

    private volatile boolean threadNumChange;
    private int threadNum;

    private volatile boolean hashSizeChange;
    private int hashSize;

    private BufferedReader reader;

    private BufferedWriter writer;

    private EngineCallBack cb;

    private Thread thread;

    private Random random;

    private List<List<String>> pvLines = new ArrayList<>(); // 存储多个 pv 线路
    private int currentPvIndex = 0;
    private int multiPvCount = 1; // 多PV数量

    public enum AnalysisModel {
        FIXED_TIME,
        FIXED_STEPS,
        INFINITE;
    }

    public Engine(EngineConfig ec, EngineCallBack cb) throws IOException {
        this.protocol = ec.getProtocol();
        this.cb = cb;
        this.random = new SecureRandom();

        process = Runtime.getRuntime().exec(ec.getPath(), null, PathUtils.getParentDir(ec.getPath()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        thread = Thread.startVirtualThread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if (line.contains("nps")) {
                        thinkDetail(line);
                    } else if (line.contains("bestmove")) {
                        bestMove(line);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        cmd(protocol);

        // 设置多PV选项
        // 设置多PV选项
        if (ec.getMultiPV() > 1) {
            this.multiPvCount = ec.getMultiPV();
            if ("uci".equals(this.protocol)) {
                cmd("setoption name MultiPV value " + ec.getMultiPV());
            } else if ("ucci".equals(this.protocol)) {
                cmd("setoption MultiPV " + ec.getMultiPV());
            }
            System.out.println("引擎初始化: 启用多PV模式，数量: " + ec.getMultiPV()); // 调试输出
        }

        for (Map.Entry<String, String> entry : ec.getOptions().entrySet()) {
            if ("uci".equals(this.protocol)) {
                cmd("setoption name " + entry.getKey() + " value " + entry.getValue());
            } else if ("ucci".equals(this.protocol)) {
                cmd("setoption " + entry.getKey() + " " + entry.getValue());
            }
        }
    }

    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String test(String filePath, LinkedHashMap<String, String> options) {
        Process p = null;
        Thread h = null;
        BufferedWriter bw = null;
        BufferedReader br = null;
        try {
            p = Runtime.getRuntime().exec(filePath);
            bw = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            AtomicBoolean f = new AtomicBoolean(false);
            BufferedReader finalBr = br;
            (h = Thread.ofVirtual().unstarted(() -> {
                try {
                    String line;
                    while ((line = finalBr.readLine()) != null) {
                        if ("uciok".equals(line) || "ucciok".equals(line) ) {
                            f.set(true);
                        }
                        if (line.startsWith("option") && line.contains("name") && line.contains("type") && line.contains("default")
                            && !line.contains("Threads") && !line.contains("Hash")) {

                            String[] str = line.split("name|type|default");
                            String key = str[1].trim();
                            String value = str[3].trim().split(" ")[0];
                            options.put(key, value);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).start();

            bw.write("uci" + System.getProperty("line.separator"));
            bw.flush();
            Thread.sleep(1000);
            if (f.get()) {
                return "uci";
            }

            bw.write("ucci" + System.getProperty("line.separator"));
            bw.flush();
            Thread.sleep(1000);
            if (f.get()) {
                return "ucci";
            }

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
            if (h.isAlive()) {
                h.interrupt();
            }
            try {
                if (bw != null) {
                    bw.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean validateMove(String move) {
        if (StringUtils.isEmpty(move) || move.length() != 4) {
            return false;
        }
        if (move.charAt(0) < 'a' || move.charAt(0) > 'i' || move.charAt(2) < 'a' || move.charAt(2) > 'i') {
            return false;
        }
        if (move.charAt(1) < '0' || move.charAt(1) > '9' || move.charAt(3) < '0' || move.charAt(3) > '9') {
            return false;
        }
        return true;
    }
    private void bestMove(String msg) {
        String[] str = msg.split(" ");
        if (str.length < 2 || !validateMove(str[1])) {
            return;
        }
        if (Properties.getInstance().getEngineDelayEnd() > 0 && Properties.getInstance().getEngineDelayEnd() >= Properties.getInstance().getEngineDelayStart()) {
            int t = random.nextInt(Properties.getInstance().getEngineDelayStart(), Properties.getInstance().getEngineDelayEnd());
            sleep(t);
        }
        cb.bestMove(str[1], str.length == 4 ? str[3] : null);
    }
//    private void thinkDetail(String msg) {
//        String[] str = msg.split(" ");
//        ThinkData td = new ThinkData();
//        List<String> detail = new ArrayList<>();
//        td.setDetail(detail);
//        int flag = 0;
//        for (int i = 0; i < str.length; i++) {
//            if (flag != 0) {
//                if (flag == 6) {
//                    detail.add(str[i]);
//                } else {
//                    if (StringUtils.isDigit(str[i])) {
//                        if (flag == 1) {
//                            td.setNps(Long.parseLong(str[i]));
//
//                        } else if (flag == 2) {
//                            td.setTime(Long.parseLong(str[i]));
//
//                        } else if (flag == 3) {
//                            td.setDepth(Integer.parseInt(str[i]));
//                        } else if (flag == 4) {
//                            td.setMate(Integer.parseInt(str[i]));
//
//                        } else if (flag == 5) {
//                            td.setScore(Integer.parseInt(str[i]));
//                        }
//                        flag = 0;
//                    } else {
//                        continue;
//                    }
//                }
//            } else {
//                if ("depth".equals(str[i])) {
//                    flag = 3;
//                } else if ("score".equals(str[i])) {
//                    if ("mate".equals(str[i + 1])) {
//                        flag = 4;
//                    } else {
//                        flag = 5;
//                    }
//                } else if ("mate".equals(str[i])) {
//                    flag = 4;
//                } else if ("nps".equals(str[i])) {
//                    flag = 1;
//                } else if ("time".equals(str[i])) {
//                    flag = 2;
//                } else if ("pv".equals(str[i])) {
//                    flag = 6;
//                }
//            }
//        }
//        // 如果有多个 pv，可能被分多行输出，但这里只取第一个
//        // 但我们希望在收到所有 pv 后，统一处理
//        // 所以我们不直接调用 cb.thinkDetail(td)，而是缓存
//        // 但在当前架构下，我们只能按行处理
//
//        if (td.getDetail().size() > 0) {
//            cb.thinkDetail(td);
//        }
//    }

    // 修改 thinkDetail 方法来处理多PV
//    private void thinkDetail(String msg) {
//        String[] str = msg.split(" ");
//        ThinkData td = new ThinkData();
//        List<String> detail = new ArrayList<>();
//        td.setDetail(detail);
//
//        int flag = 0;
//        int currentPv = 1; // 默认第一个PV
//
//        for (int i = 0; i < str.length; i++) {
//            if (flag != 0) {
//                if (flag == 6) {
//                    detail.add(str[i]);
//                } else if (flag == 7) { // 处理 multipv
//                    currentPv = Integer.parseInt(str[i]);
//                    td.setPvIndex(currentPv);
//                } else {
//                    if (StringUtils.isDigit(str[i])) {
//                        if (flag == 1) {
//                            td.setNps(Long.parseLong(str[i]));
//                        } else if (flag == 2) {
//                            td.setTime(Long.parseLong(str[i]));
//                        } else if (flag == 3) {
//                            td.setDepth(Integer.parseInt(str[i]));
//                        } else if (flag == 4) {
//                            td.setMate(Integer.parseInt(str[i]));
//                        } else if (flag == 5) {
//                            td.setScore(Integer.parseInt(str[i]));
//                        }
//                        flag = 0;
//                    } else {
//                        continue;
//                    }
//                }
//            } else {
//                switch (str[i]) {
//                    case "depth":
//                        flag = 3;
//                        break;
//                    case "score":
//                        flag = ("mate".equals(str[i + 1])) ? 4 : 5;
//                        break;
//                    case "mate":
//                        flag = 4;
//                        break;
//                    case "nps":
//                        flag = 1;
//                        break;
//                    case "time":
//                        flag = 2;
//                        break;
//                    case "pv":
//                        flag = 6;
//                        break;
//                    case "multipv":
//                        flag = 7;
//                        break;
//                }
//            }
//        }
//
//        // 如果有多个PV，收集所有PV信息
//        if (td.getDetail().size() > 0) {
//            cb.thinkDetail(td);
//
//            // 如果是多PV模式，收集所有候选着法
//            if (multiPvCount > 1) {
//                collectMultiplePv(td, currentPv);
//            }
//        }
//    }

    // 在 Engine 类中修复 thinkDetail 方法
    private void thinkDetail(String msg) {
        String[] str = msg.split(" ");
        ThinkData td = new ThinkData();
        List<String> detail = new ArrayList<>();
        td.setDetail(detail);

        int flag = 0;
        int currentPv = 1; // 默认第一个PV

        for (int i = 0; i < str.length; i++) {
            if (flag != 0) {
                if (flag == 6) {
                    // pv 着法序列
                    detail.add(str[i]);
                } else if (flag == 7) {
                    // multipv 索引
                    try {
                        currentPv = Integer.parseInt(str[i]);
                        td.setPvIndex(currentPv);
                    } catch (NumberFormatException e) {
                        // 忽略解析错误，保持默认值
                    }
                    flag = 0;
                } else {
                    // 处理其他标志
                    String token = str[i];
                    if (isNumeric(token) || (token.startsWith("-") && token.length() > 1 && isNumeric(token.substring(1)))) {
                        try {
                            if (flag == 1) {
                                td.setNps(Long.parseLong(token));
                            } else if (flag == 2) {
                                td.setTime(Long.parseLong(token));
                            } else if (flag == 3) {
                                td.setDepth(Integer.parseInt(token));
                            } else if (flag == 4) {
                                td.setMate(Integer.parseInt(token));
                            } else if (flag == 5) {
                                td.setScore(Integer.parseInt(token));
                            }
                            flag = 0;
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                            flag = 0;
                        }
                    } else {
                        // 如果当前不是数字，继续寻找
                        continue;
                    }
                }
            } else {
                switch (str[i]) {
                    case "depth":
                        flag = 3;
                        break;
                    case "score":
                        // 检查下一个token是cp还是mate
                        if (i + 1 < str.length) {
                            if ("cp".equals(str[i + 1])) {
                                flag = 5; // 分数
                                i++; // 跳过cp
                            } else if ("mate".equals(str[i + 1])) {
                                flag = 4; // 杀棋
                                i++; // 跳过mate
                            }
                        }
                        break;
                    case "mate":
                        flag = 4;
                        break;
                    case "nps":
                        flag = 1;
                        break;
                    case "time":
                        flag = 2;
                        break;
                    case "pv":
                        flag = 6;
                        break;
                    case "multipv":
                        flag = 7;
                        break;
                }
            }
        }

        // 如果有多个PV，收集所有PV信息
        if (!detail.isEmpty()) {
            cb.thinkDetail(td);

            // 如果是多PV模式，收集所有候选着法
            if (multiPvCount > 1) {
                collectMultiplePv(td, currentPv);
            }
        }
    }

    // 辅助方法：判断字符串是否为数字
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // 判断是否是info输出的关键字
    private boolean isInfoKeyword(String token) {
        return "depth".equals(token) || "seldepth".equals(token) ||
                "multipv".equals(token) || "score".equals(token) ||
                "mate".equals(token) || "nodes".equals(token) ||
                "nps".equals(token) || "hashfull".equals(token) ||
                "tbhits".equals(token) || "time".equals(token) ||
                "pv".equals(token) || "currmove".equals(token) ||
                "currmovenumber".equals(token);
    }

    // 收集多个PV信息
    private void collectMultiplePv(ThinkData td, int pvIndex) {
        if (pvIndex < 1 || pvIndex > multiPvCount) {
            return;
        }

        // 确保 pvLines 有足够的空间
        while (pvLines.size() < pvIndex) {
            pvLines.add(new ArrayList<>());
        }

        // 更新当前PV的着法
        if (td.getDetail() != null && !td.getDetail().isEmpty()) {
            pvLines.set(pvIndex - 1, new ArrayList<>(td.getDetail()));

            // 如果收集到了所有PV，通知回调
            if (pvIndex == multiPvCount && isAllPvCollected()) {
                List<String> allFirstMoves = new ArrayList<>();
                for (List<String> pvLine : pvLines) {
                    if (!pvLine.isEmpty()) {
                        allFirstMoves.add(pvLine.get(0));
                    }
                }
                cb.showMultiplePv(allFirstMoves);
            }
        }
    }

    // 检查是否所有PV都已收集
    private boolean isAllPvCollected() {
        if (pvLines.size() < multiPvCount) {
            return false;
        }
        for (int i = 0; i < multiPvCount; i++) {
            if (pvLines.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 清空PV缓存
    public void clearPvCache() {
        pvLines.clear();
        currentPvIndex = 0;
    }


    public void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo) {

        stop();
        clearPvCache(); // 清空PV缓存

        Thread.startVirtualThread(() -> {
            if (Properties.getInstance().getBookSwitch()) {
                long s = System.currentTimeMillis();
                List<BookData> results = OpenBookManager.getInstance().queryBook(board, redGo, moves.size() / 2 >= Properties.getInstance().getOffManualSteps());
                System.out.println("查询库时间" + (System.currentTimeMillis() - s));
                this.cb.showBookResults(results);
                if (results.size() > 0 && this.analysisModel != AnalysisModel.INFINITE) {
                    if (Properties.getInstance().getBookDelayEnd() > 0 && Properties.getInstance().getBookDelayEnd() >= Properties.getInstance().getBookDelayStart()) {
                        int t = random.nextInt(Properties.getInstance().getBookDelayStart(), Properties.getInstance().getBookDelayEnd());
                        sleep(t);
                    }
                    this.cb.bestMove(results.get(0).getMove(), null);
                    return;
                }

            }
            this.analysis(fenCode, moves);
        });

    }

    private void analysis(String fenCode, List<String> moves) {
        stop();

        if (threadNumChange) {
            cmd(("uci".equals(this.protocol) ? "setoption name Threads value " : "setoption Threads ") + threadNum);
            this.threadNumChange = false;
        }
        if (hashSizeChange) {
            cmd(("uci".equals(this.protocol) ? "setoption name Hash value " : "setoption Hash ") + hashSize);
            this.hashSizeChange = false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("position fen ").append(fenCode);
        if (moves != null && moves.size() > 0) {
            sb.append(" moves");
            for (String move : moves) {
                sb.append(" ").append(move);
            }
        }
        cmd(sb.toString());

        if (analysisModel == AnalysisModel.FIXED_STEPS) {
            cmd("go depth " + analysisValue);
        } else if (analysisModel == AnalysisModel.FIXED_TIME) {
            cmd("go movetime " + analysisValue);
        } else {
            cmd("go infinite");
        }
    }

    public void stop() {
        cmd("stop");
    }

    private void cmd(String command) {
        System.out.println(command);
        try {
            writer.write(command + System.getProperty("line.separator"));
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setThreadNum(int threadNum) {
        if (threadNum != this.threadNum) {
            this.threadNum = threadNum;
            this.threadNumChange = true;
        }

    }

    public void setHashSize(int hashSize) {
        if (hashSize != this.hashSize) {
            this.hashSize = hashSize;
            this.hashSizeChange = true;
        }
    }

    public void setAnalysisModel(AnalysisModel model, long v) {
        this.analysisModel = model;
        this.analysisValue = v;
    }

    public void close() {
        try {
            if (process.isAlive()) {
                cmd("quit");
            }

            if (thread.isAlive()) {
                thread.interrupt();
            }

            if (process.isAlive()) {
                process.destroy();
            }

            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
