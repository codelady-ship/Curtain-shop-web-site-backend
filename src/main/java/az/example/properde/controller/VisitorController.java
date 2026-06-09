package az.example.properde.controller;

import az.example.properde.dao.entity.Visitor;
import az.example.properde.service.VisitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/visitors")
@RequiredArgsConstructor
public class VisitorController {
    private final VisitorService visitorService;

    @GetMapping
    public List<Visitor> list() {
        return visitorService.list();
    }

    @DeleteMapping("/{id}/soft-delete")
    public void softDelete(@PathVariable Long id) {
        visitorService.softDelete(id);
    }
}
