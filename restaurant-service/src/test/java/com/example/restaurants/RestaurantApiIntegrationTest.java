package com.example.restaurants;

import com.example.restaurants.restaurant.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.location=classpath:/application-test.yml",
        "spring.datasource.url=jdbc:h2:mem:restaurants;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.application.name=restaurant-service", "app.seed.enabled=false"})
@AutoConfigureMockMvc
class RestaurantApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository menu;
    Restaurant pizza;
    MenuItem margherita;
    MenuItem lemonade;
    MenuItem foreignDish;
    @BeforeEach void prepare() {
        menu.deleteAll(); restaurants.deleteAll();
        pizza=restaurants.save(new Restaurant("Пиццерия","Москва"));
        margherita=menu.save(new MenuItem(pizza,"Маргарита",new BigDecimal("550.00")));
        lemonade=menu.save(new MenuItem(pizza,"Лимонад",new BigDecimal("150.00")));
        Restaurant sushi=restaurants.save(new Restaurant("Суши","Москва"));
        foreignDish=menu.save(new MenuItem(sushi,"Ролл",new BigDecimal("650.00")));
    }
    private String quotePath() { return "/internal/restaurants/"+pizza.getId()+"/quote"; }
    private String cart() { return "{\"items\":[{\"menuItemId\":"+margherita.getId()+",\"quantity\":2},{\"menuItemId\":"+lemonade.getId()+",\"quantity\":1}]}"; }
    @Test void calculates1250FromStoredMenuWithoutMutatingIt() throws Exception {
        mvc.perform(post(quotePath()).contentType("application/json").content(cart()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.restaurantId").value(pizza.getId()))
                .andExpect(jsonPath("$.items[0].unitPrice").value(550))
                .andExpect(jsonPath("$.items[0].lineTotal").value(1100))
                .andExpect(jsonPath("$.items[1].lineTotal").value(150))
                .andExpect(jsonPath("$.totalAmount").value(1250));
        assertThat(menu.count()).isEqualTo(3);
        assertThat(menu.findById(margherita.getId()).orElseThrow().getPrice()).isEqualByComparingTo("550.00");
    }
    @Test void restaurantAndDishAvailabilityControlQuote() throws Exception {
        String restaurantPath="/api/restaurants/"+pizza.getId();
        mvc.perform(patch(restaurantPath+"/availability").contentType("application/json").content("{\"open\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(false));
        mvc.perform(post(quotePath()).contentType("application/json").content(cart()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESTAURANT_NOT_AVAILABLE"));
        mvc.perform(patch(restaurantPath+"/availability").contentType("application/json").content("{\"open\":true}"))
                .andExpect(status().isOk());
        mvc.perform(patch(restaurantPath+"/menu/"+margherita.getId()+"/availability").contentType("application/json").content("{\"available\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post(quotePath()).contentType("application/json").content(cart())).andExpect(status().isConflict());
        mvc.perform(patch(restaurantPath+"/menu/"+margherita.getId()+"/availability").contentType("application/json").content("{\"available\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post(quotePath()).contentType("application/json").content(cart())).andExpect(status().isOk());
    }
    @Test void rejectsForeignAndMissingDishesAndRestaurants() throws Exception {
        for (Long id:new Long[]{foreignDish.getId(),999999L}) {
            mvc.perform(post(quotePath()).contentType("application/json").content("{\"items\":[{\"menuItemId\":"+id+",\"quantity\":1}]}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESTAURANT_OR_DISH_NOT_FOUND"));
        }
        mvc.perform(get("/api/restaurants/999999/menu")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/restaurants/999999/quote").contentType("application/json").content(cart())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/restaurants/"+pizza.getId()+"/menu/"+foreignDish.getId()+"/availability")
                .contentType("application/json").content("{\"available\":false}")).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"items\":[]}","{\"items\":[null]}",
            "{\"items\":[{\"menuItemId\":1,\"quantity\":0}]}",
            "{\"items\":[{\"menuItemId\":1,\"quantity\":21}]}",
            "{\"items\":[{\"menuItemId\":1,\"quantity\":2.5}]}",
            "{\"items\":[{\"menuItemId\":-1,\"quantity\":1}]}","broken"})
    void rejectsBadQuoteInput(String body) throws Exception {
        mvc.perform(post(quotePath()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ORDER"));
    }
    @Test void rejectsDuplicateAndTooManyItems() throws Exception {
        String item="{\"menuItemId\":"+margherita.getId()+",\"quantity\":1}";
        mvc.perform(post(quotePath()).contentType("application/json").content("{\"items\":["+item+","+item+"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ORDER"));
        String many=java.util.stream.IntStream.rangeClosed(1,21).mapToObj(i->"{\"menuItemId\":"+i+",\"quantity\":1}")
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post(quotePath()).contentType("application/json").content("{\"items\":["+many+"]}"))
                .andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","null","10000000000"})
    void rejectsInvalidPrice(String price) throws Exception {
        mvc.perform(post("/api/restaurants/"+pizza.getId()+"/menu").contentType("application/json")
                .content("{\"name\":\"Суп\",\"price\":"+price+"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESTAURANT"));
        assertThat(menu.count()).isEqualTo(3);
    }
    @Test void createsRestaurantAndMenuWithRetrievableLocation() throws Exception {
        String location=mvc.perform(post("/api/restaurants").contentType("application/json")
                .content("{\"name\":\"  Кафе  \",\"address\":\"  Центр  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Кафе"))
                .andExpect(jsonPath("$.open").value(true)).andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.address").value("Центр"));
        String itemLocation=mvc.perform(post(location+"/menu").contentType("application/json").content("{\"name\":\"Суп\",\"price\":200.50}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(get(itemLocation)).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(200.50));
        mvc.perform(get(location+"/menu")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void rejectsEmptyAvailabilityFlagsAndNames() throws Exception {
        mvc.perform(patch("/api/restaurants/"+pizza.getId()+"/availability").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/restaurants/"+pizza.getId()+"/menu/"+margherita.getId()+"/availability")
                .contentType("application/json").content("{\"available\":null}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/restaurants").contentType("application/json").content("{\"name\":\"  \",\"address\":\"Москва\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void documentsOnlyPublicApi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.paths['/api/restaurants']").exists())
                .andExpect(jsonPath("$.paths['/internal/restaurants/{id}/quote']").doesNotExist());
    }
}
