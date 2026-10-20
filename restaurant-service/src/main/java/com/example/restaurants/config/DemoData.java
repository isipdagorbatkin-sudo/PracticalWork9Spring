package com.example.restaurants.config;
import com.example.restaurants.restaurant.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@Component
@ConditionalOnProperty(name="app.seed.enabled", havingValue="true", matchIfMissing=true)
public class DemoData implements CommandLineRunner {
    private final RestaurantRepository restaurants;
    private final MenuItemRepository menu;
    public DemoData(RestaurantRepository restaurants, MenuItemRepository menu) { this.restaurants=restaurants; this.menu=menu; }
    @Override @Transactional
    public void run(String... args) {
        if (restaurants.count()!=0) return;
        Restaurant pizza=restaurants.save(new Restaurant("Пиццерия у дома", "Москва, Пушкина, 10"));
        menu.save(new MenuItem(pizza, "Маргарита", new BigDecimal("550.00")));
        menu.save(new MenuItem(pizza, "Лимонад", new BigDecimal("150.00")));
        Restaurant sushi=restaurants.save(new Restaurant("Суши рядом", "Москва, Садовая, 5"));
        menu.save(new MenuItem(sushi, "Филадельфия", new BigDecimal("650.00")));
        menu.save(new MenuItem(sushi, "Чай", new BigDecimal("120.00")));
    }
}
