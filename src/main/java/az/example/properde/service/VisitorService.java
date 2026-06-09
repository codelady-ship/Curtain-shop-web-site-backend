package az.example.properde.service;

import az.example.properde.dao.entity.Visitor;
import az.example.properde.repository.VisitorRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitorService {
    private final VisitorRepository visitorRepository;

    public void record(HttpServletRequest request) {
        if (request == null) return;
        Visitor visitor = new Visitor();
        visitor.setIpAddress(resolveIp(request));
        visitor.setUserAgent(limit(request.getHeader("User-Agent"), 4000));
        visitor.setReferrer(limit(request.getHeader("Referer"), 1000));
        visitor.setPath(limit(request.getHeader("X-Page-Path"), 1000));
        visitor.setVisitedAt(LocalDateTime.now());
        visitorRepository.save(visitor);
    }

    @Transactional(readOnly = true)
    public List<Visitor> list() {
        return visitorRepository.findByDeletedFalseOrderByVisitedAtDesc();
    }

    public void softDelete(Long id) {
        Visitor visitor = visitorRepository.findById(id).orElseThrow(() -> new RuntimeException("Visitor not found: " + id));
        visitor.setDeleted(true);
        visitorRepository.save(visitor);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) return limit(forwarded.split(",")[0].trim(), 120);
        return limit(request.getRemoteAddr(), 120);
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
