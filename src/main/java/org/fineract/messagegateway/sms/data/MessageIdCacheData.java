package org.fineract.messagegateway.sms.data;


import java.io.Serializable;

//@RedisHash("MessageIdCache")
public class MessageIdCacheData implements Serializable {

    private String internalId;
    private String externalId;

//    public MessageIdCacheData(String internalId, String externalId){
//        this.internalId = internalId;
//        this.externalId=externalId;
//    }
//
//    public String getInternalId() {
//        return internalId;
//    }
//
//    public void setInternalId(String internalId) {
//        this.internalId = internalId;
//    }
//
//    public String getExternalId() {
//        return externalId;
//    }
//
//    public void setExternalId(String externalId) {
//        this.externalId = externalId;
//    }
}
