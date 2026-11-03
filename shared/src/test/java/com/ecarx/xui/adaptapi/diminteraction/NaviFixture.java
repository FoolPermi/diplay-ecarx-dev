package com.ecarx.xui.adaptapi.diminteraction;

import android.graphics.Bitmap;
import android.os.RemoteException;
import android.cevt.dimsupportservice.DimSupportManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NaviFixture implements INaviInteraction {
    public INavigationInteractionCallback callback;
    public boolean ongoingNavigationActive;
    public boolean handlerNavigationActive;
    public boolean resendOngoing;
    public boolean navigationMute;
    public String destinationName = "";
    public int cachedArrowId = -1;
    public String cachedArrowSvg = "";
    public final List<Boolean> ongoingTransitions = new ArrayList<>();
    public final List<String> navigationEvents = new ArrayList<>();

    // Mirrors NavigationHandler: firstFrameReady preserves active but replaces the cached event.
    public void sendNavigationEvent(String event) {
        navigationEvents.add(event);
        FixtureLog.record("navigation:" + event);
        if (event.equals("ongoingNavi")) handlerNavigationActive = true;
        else if (!event.equals("firstFrameReady")) handlerNavigationActive = false;
    }
    public void updateNavigationInfo(INavigationInfo i) {
        FixtureLog.capture("updateNavigationInfo",
            "getNavigationTurnId", i.getNavigationTurnId(),
            "getDistanceToNextGuidancePoint", i.getDistanceToNextGuidancePoint(),
            "getDistanceToDestination", i.getDistanceToDestination(),
            "getDrivingDirection", i.getDrivingDirection(), "getETA", i.getETA(),
            "getLaneInfo", i.getLaneInfo(), "getMuteState", i.getMuteState(),
            "getNavigationStatus", i.getNavigationStatus(),
            "getNextGuidancePointName", i.getNextGuidancePointName(),
            "getNavigationTurnSVG", i.getNavigationTurnSVG(),
            "getHighwayExitInfo", i.getHighwayExitInfo(),
            "getRoadCameraInfo", i.getRoadCameraInfo(), "getServiceAreaInfo", i.getServiceAreaInfo());
        // Match the recovered SDK's deduplication and one-shot resend, independently of DIM state.
        boolean active = i.getNavigationStatus() == 1;
        if (!active) {
            cachedArrowId = -1;
            cachedArrowSvg = "";
        }
        boolean mute = i.getMuteState() == 1;
        if (active != ongoingNavigationActive || mute != navigationMute || (resendOngoing && active)) {
            ongoingNavigationActive = active;
            navigationMute = mute;
            sendNavigationEvent(active ? "ongoingNavi" : "idle");
            resendOngoing = false;
            ongoingTransitions.add(active);
        }
    }
    public void updateTurnByTurnArrow(int arrow) {
        FixtureLog.capture("updateTurnByTurnArrow(int)", "arrow", arrow);
        if (cachedArrowId != arrow) {
            cachedArrowId = arrow;
            cachedArrowSvg = "";
            sendIcon(0, String.valueOf(arrow));
        }
    }
    public void updateTurnByTurnArrow(String svg) {
        FixtureLog.capture("updateTurnByTurnArrow(String)", "svg", svg);
        if (cachedArrowSvg != svg) {
            cachedArrowSvg = svg;
            cachedArrowId = -1;
            sendIcon(1, svg);
        }
    }
    private void sendIcon(int type, String value) {
        try {
            DimSupportManager.getNavigationManager().updateTurnByTurnIcon(type, value);
        } catch (RemoteException ignored) {
            // Match the shipped SDK: the cache is not rolled back and the caller sees no error.
        }
    }
    public void updateTurnByTurnArrow(Bitmap bitmap) { FixtureLog.capture("updateTurnByTurnArrow(Bitmap)", "bitmap", bitmap); }
    public void updateDistanceToNextGuidancePoint(int meters) { FixtureLog.capture("updateDistanceToNextGuidancePoint", "meters", meters); }
    public void updateDistanceToDestination(int meters) { FixtureLog.capture("updateDistanceToDestination", "meters", meters); }
    public void updateETA(int remainingSeconds) { FixtureLog.capture("updateETA", "remainingSeconds", remainingSeconds); }
    public void updateNextGuidancePointName(String name) { FixtureLog.capture("updateNextGuidancePointName", "name", name); }
    public void updateAddresses(int type, List<IAddress> addresses) {
        // Type 3 deliberately never reads coordinates or caches selectable addresses on 753.
        String name = addresses.isEmpty() ? "" : addresses.get(0).getAddressName();
        FixtureLog.capture("updateAddresses", "type", type, "count", addresses.size(), "name", name);
        if (type == TYPE_DESTINATION && !destinationName.equals(name)) {
            destinationName = name;
            sendNavigationEvent(ongoingNavigationActive ? "ongoingNavi" : "idle");
            resendOngoing = false;
        }
    }
    public void updateLaneInfo(ILaneInfo[] lanes) {
        List<Map<String, Object>> values = null;
        if (lanes != null) {
            values = new ArrayList<>();
            for (ILaneInfo lane : lanes) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("number", lane.getLaneNumber());
                value.put("iconId", lane.getLaneBackIconId());
                value.put("bitmap", lane.getLaneBackIcon());
                values.add(value);
            }
        }
        FixtureLog.capture("updateLaneInfo", "lanes", values, "count", lanes == null ? null : lanes.length,
            "componentType", ILaneInfo.class.getName());
    }
    public void updateDriveDirection(int direction) { FixtureLog.capture("updateDriveDirection", "direction", direction); }
    public void updateMuteState(int state) {
        FixtureLog.capture("updateMuteState", "state", state);
        navigationMute = state == 1;
        sendNavigationEvent(ongoingNavigationActive ? "ongoingNavi" : "idle");
        resendOngoing = false;
        ongoingTransitions.add(ongoingNavigationActive);
    }
    public void notifyTurnByTurnStarted() { FixtureLog.capture("notifyTurnByTurnStarted"); }
    public void notifyTurnByTurnStopped() { FixtureLog.capture("notifyTurnByTurnStopped"); }
    public void registerNavigationInteractionCallback(INavigationInteractionCallback value) {
        FixtureLog.capture("registerNavigationInteractionCallback"); callback = value;
    }
}
