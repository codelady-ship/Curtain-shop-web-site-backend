
package com.properde.backend.service;

import java.util.*;

public class AdminRecoveryService {

    public Map<String,Object> createCode(Map<String,String> req){
        Map<String,Object> res = new HashMap<>();
        res.put("status","CODE_SENT");
        res.put("method","EMAIL_WHATSAPP_MOCK");
        return res;
    }

    public Map<String,Object> verifyCode(Map<String,String> req){
        Map<String,Object> res = new HashMap<>();
        res.put("status","VERIFIED");
        return res;
    }

    public Map<String,Object> resetPassword(Map<String,String> req){
        Map<String,Object> res = new HashMap<>();
        res.put("status","PASSWORD_UPDATED");
        return res;
    }
}
