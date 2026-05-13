package az.example.properde.util;

import org.springframework.data.domain.*;

public class PageUtil {

    public static Pageable pageable(int page, int size){
        return PageRequest.of(page, size, Sort.by("id").descending());
    }
}