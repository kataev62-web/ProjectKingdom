package ru.projectkingdom.npc.voice;

/** Pure distance and stereo policy shared by the client player and tests. */
public final class NpcSpeechSpatialAudio {
    public static final double MAX_DISTANCE=16.0D;

    private NpcSpeechSpatialAudio() {}

    public static double volume(double distance){
        if(!Double.isFinite(distance)||distance>=MAX_DISTANCE)return 0.0D;
        if(distance<=0.0D)return 1.0D;
        return 1.0D-distance/MAX_DISTANCE;
    }

    public static double pan(double x,double z,double lookX,double lookZ){
        double facing=Math.hypot(lookX,lookZ);
        if(facing<0.000001D)return 0.0D;
        return pan((-x*lookZ+z*lookX)/facing,Math.hypot(x,z));
    }

    public static double pan(double rightProjection,double horizontalDistance){
        if(!Double.isFinite(rightProjection)||!Double.isFinite(horizontalDistance)
                ||horizontalDistance<=0.0D)return 0.0D;
        return Math.max(-1.0D,Math.min(1.0D,rightProjection/horizontalDistance));
    }
}
