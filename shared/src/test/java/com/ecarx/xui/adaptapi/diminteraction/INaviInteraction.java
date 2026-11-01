package com.ecarx.xui.adaptapi.diminteraction;

import android.graphics.Bitmap;

public interface INaviInteraction {
    void updateNavigationInfo(INavigationInfo info);
    void updateTurnByTurnArrow(int arrow);
    void updateTurnByTurnArrow(String svg);
    void updateTurnByTurnArrow(Bitmap bitmap);
    void updateDistanceToNextGuidancePoint(int meters);
    void updateDistanceToDestination(int meters);
    void updateETA(int epochSeconds);
    void updateNextGuidancePointName(String name);
    void updateLaneInfo(ILaneInfo[] lanes);
    void updateDriveDirection(int direction);
    void updateMuteState(int state);
    void notifyTurnByTurnStarted();
    void notifyTurnByTurnStopped();
    void registerNavigationInteractionCallback(INavigationInteractionCallback callback);

    // Observed on the target head unit and in the shipped 753 navigation plugin.
    interface INavigationInteractionCallback { void onDoInteractionAction(int action, IAddress address); }
    interface IAddress { }
    interface IHighwayExitInfo { }
    interface IRoadCamera { }
    interface IServiceArea { }
    interface ILaneInfo {
        int getLaneNumber();
        int getLaneBackIconId();
        Bitmap getLaneBackIcon();
    }
    // The 13 getters recovered from the target interface, without implementation-only extras.
    interface INavigationInfo {
        int getNavigationTurnId();
        long getDistanceToNextGuidancePoint();
        long getDistanceToDestination();
        int getDrivingDirection();
        long getETA();
        ILaneInfo[] getLaneInfo();
        int getMuteState();
        int getNavigationStatus();
        String getNextGuidancePointName();
        String getNavigationTurnSVG();
        IHighwayExitInfo getHighwayExitInfo();
        IRoadCamera getRoadCameraInfo();
        IServiceArea getServiceAreaInfo();
    }
}
