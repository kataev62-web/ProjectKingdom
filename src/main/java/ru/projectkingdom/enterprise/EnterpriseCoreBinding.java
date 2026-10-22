package ru.projectkingdom.enterprise;

import java.util.UUID;

/** Durable identity and last known position of one enterprise core. */
public record EnterpriseCoreBinding(UUID enterpriseId, UUID owner, EnterpriseKind kind,
                                    String dimension, long packedPos, boolean active) {
    public EnterpriseCoreBinding {
        if(enterpriseId==null||owner==null||kind==null||dimension==null||dimension.isBlank())
            throw new IllegalArgumentException("Incomplete enterprise core binding");
    }

    public EnterpriseCoreBinding suspended(){
        return new EnterpriseCoreBinding(enterpriseId,owner,kind,dimension,packedPos,false);
    }
}
