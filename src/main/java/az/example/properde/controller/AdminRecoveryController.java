
package com.properde.backend.controller;

import com.properde.backend.service.AdminRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/recovery")
public class AdminRecoveryController {

    private final AdminRecoveryService service;

    public AdminRecoveryController(AdminRecoveryService service) {
        this.service = service;
    }

    @PostMapping("/request")
    public ResponseEntity<?> request(@RequestBody Map<String,String> req){
        return ResponseEntity.ok(service.createCode(req));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String,String> req){
        return ResponseEntity.ok(service.verifyCode(req));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody Map<String,String> req){
        return ResponseEntity.ok(service.resetPassword(req));
    }
}
