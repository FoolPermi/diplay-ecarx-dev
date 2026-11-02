package android.cevt.dimsupportservice;

import java.util.ArrayList;
import java.util.List;

public final class DimSupportManager {
    private static final DimSupportManager INSTANCE = new DimSupportManager();
    public static final List<String> writes = new ArrayList<>();
    public static DimSupportManager getNavigationManager() { return INSTANCE; }
    public void updateTurnByTurnIcon(int type, String value) { writes.add(type + ":" + value); }
    public void updateTurnByTurnNextTurn(int meters, String road) { writes.add(meters + ":" + road); }
}
