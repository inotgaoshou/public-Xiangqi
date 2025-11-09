package com.sojourners.chess.enginee;


import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.ThinkData;

import java.util.List;

/**
 * 引擎回调
 */
public interface EngineCallBack {

    void bestMove(String first, String second);

    void bestMove(List<String[]> pvLines, int selectedPVIndex);

    void thinkDetail(ThinkData td);

    void showBookResults(List<BookData> list);

    void showMultiplePv(List<String> pvMoves); // 新增
}
