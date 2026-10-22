package ru.projectkingdom.administration;

import java.util.UUID;

public record PrisonRecord(UUID id,UUID townId,UUID prisoner,UUID caseId,String cellDimension,long cellPos,
                           long sentenceStart,long sentenceEnd,String reason,boolean active) {
    public static final long MAX_SENTENCE_TICKS=20L*60*60*24*30;
    public PrisonRecord {
        if(id==null||townId==null||prisoner==null||cellDimension==null||cellDimension.isBlank()||cellDimension.length()>96
                ||sentenceStart<0||sentenceEnd<=sentenceStart||sentenceEnd-sentenceStart>MAX_SENTENCE_TICKS||!JusticeCase.valid(reason,96))
            throw new IllegalArgumentException("Invalid prison record");
    }
    public PrisonRecord release(){return new PrisonRecord(id,townId,prisoner,caseId,cellDimension,cellPos,sentenceStart,sentenceEnd,reason,false);}
}
