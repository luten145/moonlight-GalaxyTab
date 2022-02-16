package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(int resolutionWidth, int resolutionHeight, short totalFps, short receivedFps,
                      short renderedFps, int ping,int variance, short decodeTime,
                      float packetLossPercentage);

}
