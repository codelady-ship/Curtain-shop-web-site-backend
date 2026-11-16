package az.example.properde.controller;

import az.example.properde.exception.GlobalExceptionHandler;
import az.example.properde.exception.NotFoundException;
import az.example.properde.service.FileStorageService;
import az.example.properde.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/products/{id} for a missing product answers 404, not 500. */
class ProductNotFoundTest {

    @Test
    void missingProductIs404() throws Exception {
        ProductService products = mock(ProductService.class);
        when(products.get(999L)).thenThrow(new NotFoundException("Product not found: 999"));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ProductController(products, mock(FileStorageService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: 999"));
    }
}
