package ru.projectkingdom.administration;

import java.util.UUID;

public record JusticeCase(UUID id,UUID townId,UUID complainant,UUID defendant,UUID judge,Status status,
                          String summary,String decision,long fineCopper,long createdAt) {
    public enum Status {OPEN,HEARING,DECIDED,CLOSED}
    public JusticeCase {
        if(id==null||townId==null||complainant==null||defendant==null||status==null||!valid(summary,96)
                ||decision==null||(!decision.isEmpty()&&!valid(decision,192))||fineCopper<0||fineCopper>1_000_000||createdAt<0)
            throw new IllegalArgumentException("Invalid justice case");
    }
    public JusticeCase assignJudge(UUID value){if(value==null)throw new IllegalArgumentException("judge");return new JusticeCase(id,townId,complainant,defendant,value,Status.HEARING,summary,decision,fineCopper,createdAt);}
    public JusticeCase decide(String text,long fine){if(judge==null||!valid(text,192)||fine<0||fine>1_000_000)throw new IllegalArgumentException("decision");return new JusticeCase(id,townId,complainant,defendant,judge,Status.DECIDED,summary,text,fine,createdAt);}
    static boolean valid(String value,int max){return value!=null&&!value.isBlank()&&value.length()<=max&&value.indexOf('§')<0&&value.chars().noneMatch(Character::isISOControl);}
}
