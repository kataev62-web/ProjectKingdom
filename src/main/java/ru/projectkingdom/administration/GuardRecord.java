package ru.projectkingdom.administration;

import java.util.UUID;

public record GuardRecord(UUID id,UUID townId,UUID guardId,String name,String patrolDimension,
                          int minChunkX,int minChunkZ,int maxChunkX,int maxChunkZ,boolean active) {
    public GuardRecord {
        if(id==null||townId==null||guardId==null||!JusticeCase.valid(name,48)||patrolDimension==null||patrolDimension.isBlank()
                ||patrolDimension.length()>96||minChunkX>maxChunkX||minChunkZ>maxChunkZ
                ||(long)maxChunkX-minChunkX>32||(long)maxChunkZ-minChunkZ>32)throw new IllegalArgumentException("Invalid guard record");
    }
    public GuardRecord deactivate(){return new GuardRecord(id,townId,guardId,name,patrolDimension,minChunkX,minChunkZ,maxChunkX,maxChunkZ,false);}
}
