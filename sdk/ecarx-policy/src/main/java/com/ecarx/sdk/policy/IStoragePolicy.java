package com.ecarx.sdk.policy;
public interface IStoragePolicy {
    public static final int VOLUME_GKUI_PRIVATE_COMMON = 5;
    public static final int VOLUME_GKUI_PRIVATE_MAP = 4;
    public static final int VOLUME_GKUI_PRIVATE_VR_RES = 3;
    public static final int VOLUME_USB_FLASH_DISK_1 = 1;
    public static final int VOLUME_USB_FLASH_DISK_2 = 2;
    public @interface VolumeType {
    }

    String getVolumeFullPath(@VolumeType int i);

    String getVolumeName(@VolumeType int i);
}
