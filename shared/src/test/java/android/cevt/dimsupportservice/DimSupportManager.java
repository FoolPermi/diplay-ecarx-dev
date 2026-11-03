package android.cevt.dimsupportservice;

import android.os.RemoteException;
import com.ecarx.xui.adaptapi.diminteraction.DimInteraction;
import java.util.ArrayList;
import java.util.List;

/** Records calls after AdaptAPI filtering, at the firmware manager boundary. */
public final class DimSupportManager {
    private static final NavigationManager MANAGER = new NavigationManager();
    public static final List<String> writes = new ArrayList<>();
    public static boolean available = true;
    public static int failedIconWrites, iconAttempts;
    public static boolean failNextTurn;

    public static NavigationManager getNavigationManager() {
        if (!available) throw new IllegalStateException("fixture manager unavailable");
        return MANAGER;
    }

    public static void reset() {
        writes.clear();
        available = true;
        failedIconWrites = iconAttempts = 0;
        failNextTurn = false;
    }

    public static final class NavigationManager {
        public void updateTurnByTurnIcon(int type, String value) throws RemoteException {
            iconAttempts++;
            if (failedIconWrites > 0) {
                failedIconWrites--;
                throw new RemoteException("fixture icon unavailable");
            }
            writes.add("icon:" + type + ":" + value);
        }
        public void updateTurnByTurnNextTurn(int meters, String road) throws RemoteException {
            if (failNextTurn) throw new RemoteException("fixture next turn unavailable");
            writes.add("next:" + meters + ":" + road);
        }
        public void updateOngoingNavigation(boolean active, String destination, boolean muted) {
            writes.add("ongoing:" + active + ":" + destination + ":" + muted);
            DimInteraction.navi.sendNavigationEvent(active ? "ongoingNavi" : "idle");
        }
        public void updateDistanceToDestination(int meters) { writes.add("distance:" + meters); }
        public void updateEta(int seconds) { writes.add("eta:" + seconds); }
    }
}
