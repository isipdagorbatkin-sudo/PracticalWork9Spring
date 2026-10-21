const state = {
    restaurants: [],
    selectedRestaurant: null,
    menu: [],
    cart: new Map(),
    order: null,
    delivery: null
};

const elements = {
    restaurantList: document.querySelector("#restaurant-list"),
    refreshButton: document.querySelector("#refresh-button"),
    menuTitle: document.querySelector("#menu-title"),
    menuSubtitle: document.querySelector("#menu-subtitle"),
    menuGrid: document.querySelector("#menu-grid"),
    cartItems: document.querySelector("#cart-items"),
    cartCount: document.querySelector("#cart-count"),
    cartTotal: document.querySelector("#cart-total"),
    clearCart: document.querySelector("#clear-cart"),
    checkoutForm: document.querySelector("#checkout-form"),
    orderButton: document.querySelector("#order-button"),
    trackingCard: document.querySelector("#tracking-card"),
    toast: document.querySelector("#toast")
};

const money = new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
});

let toastTimer;

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function request(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers
        }
    });

    const text = await response.text();
    let body = null;
    if (text) {
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
    }

    if (!response.ok) {
        const message = body?.message || `Ошибка запроса: ${response.status}`;
        throw new Error(message);
    }

    return body;
}

function showToast(message, type = "success") {
    clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast show ${type}`;
    toastTimer = setTimeout(() => {
        elements.toast.className = "toast";
    }, 3600);
}

function setButtonLoading(button, loading, loadingText) {
    if (loading) {
        button.dataset.label = button.textContent;
        button.textContent = loadingText;
        button.disabled = true;
    } else {
        button.textContent = button.dataset.label || button.textContent;
        button.disabled = false;
    }
}

async function loadRestaurants() {
    elements.restaurantList.innerHTML = '<div class="skeleton wide"></div><div class="skeleton wide"></div>';
    try {
        state.restaurants = await request("/api/restaurants");
        renderRestaurants();
        const firstOpen = state.restaurants.find(restaurant => restaurant.open);
        if (firstOpen) {
            await selectRestaurant(firstOpen.id);
        }
    } catch (error) {
        elements.restaurantList.innerHTML = `
            <div class="error-state">
                <span>⚠️</span>
                <strong>Не удалось получить рестораны</strong>
                <p>${escapeHtml(error.message)}. Проверьте, что сервисы запущены.</p>
            </div>`;
        showToast(error.message, "error");
    }
}

function renderRestaurants() {
    if (!state.restaurants.length) {
        elements.restaurantList.innerHTML = '<div class="empty-state"><strong>Ресторанов пока нет</strong></div>';
        return;
    }

    elements.restaurantList.innerHTML = state.restaurants.map((restaurant, index) => `
        <button class="restaurant-card ${state.selectedRestaurant?.id === restaurant.id ? "active" : ""}"
                type="button" data-restaurant-id="${restaurant.id}" ${restaurant.open ? "" : "disabled"}>
            <span class="restaurant-icon">${index % 2 === 0 ? "🍕" : "🍣"}</span>
            <span>
                <strong>${escapeHtml(restaurant.name)}</strong>
                <p>${escapeHtml(restaurant.address)}</p>
            </span>
            <span class="availability ${restaurant.open ? "" : "closed"}">${restaurant.open ? "Открыт" : "Закрыт"}</span>
        </button>`).join("");

    elements.restaurantList.querySelectorAll("[data-restaurant-id]").forEach(button => {
        button.addEventListener("click", () => selectRestaurant(Number(button.dataset.restaurantId)));
    });
}

async function selectRestaurant(id) {
    const restaurant = state.restaurants.find(item => item.id === id);
    if (!restaurant || !restaurant.open) return;

    if (state.selectedRestaurant && state.selectedRestaurant.id !== id && state.cart.size) {
        state.cart.clear();
        renderCart();
        showToast("Корзина очищена: заказ оформляется из одного ресторана");
    }

    state.selectedRestaurant = restaurant;
    renderRestaurants();
    elements.menuTitle.textContent = restaurant.name;
    elements.menuSubtitle.textContent = restaurant.address;
    elements.menuGrid.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div>';

    try {
        state.menu = await request(`/api/restaurants/${id}/menu`);
        renderMenu();
    } catch (error) {
        elements.menuGrid.innerHTML = `<div class="error-state"><strong>Меню недоступно</strong><p>${escapeHtml(error.message)}</p></div>`;
        showToast(error.message, "error");
    }
}

function renderMenu() {
    if (!state.menu.length) {
        elements.menuGrid.innerHTML = '<div class="empty-state"><strong>В меню пока нет блюд</strong></div>';
        return;
    }

    elements.menuGrid.innerHTML = state.menu.map((item, index) => `
        <article class="menu-card ${item.available ? "" : "unavailable"}">
            <div class="dish-icon">${index % 4 === 0 ? "🍕" : index % 4 === 1 ? "🥤" : index % 4 === 2 ? "🍣" : "🍵"}</div>
            <div class="menu-card-footer">
                <div>
                    <strong>${escapeHtml(item.name)}</strong>
                    <span class="price">${money.format(item.price)}</span>
                </div>
                <button class="add-button" type="button" data-menu-item-id="${item.id}"
                        aria-label="Добавить ${escapeHtml(item.name)}" ${item.available ? "" : "disabled"}>+</button>
            </div>
        </article>`).join("");

    elements.menuGrid.querySelectorAll("[data-menu-item-id]").forEach(button => {
        button.addEventListener("click", () => addToCart(Number(button.dataset.menuItemId)));
    });
}

function addToCart(id) {
    const item = state.menu.find(menuItem => menuItem.id === id);
    if (!item || !item.available) return;
    const current = state.cart.get(id);
    const quantity = Math.min((current?.quantity || 0) + 1, 20);
    state.cart.set(id, { ...item, quantity });
    renderCart();
    showToast(`${item.name} добавлен в корзину`);
}

function changeQuantity(id, delta) {
    const current = state.cart.get(id);
    if (!current) return;
    const quantity = current.quantity + delta;
    if (quantity <= 0) {
        state.cart.delete(id);
    } else {
        state.cart.set(id, { ...current, quantity: Math.min(quantity, 20) });
    }
    renderCart();
}

function renderCart() {
    const items = [...state.cart.values()];
    const count = items.reduce((sum, item) => sum + item.quantity, 0);
    const total = items.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0);

    elements.cartCount.textContent = count;
    elements.cartTotal.textContent = money.format(total);
    elements.orderButton.disabled = items.length === 0;

    if (!items.length) {
        elements.cartItems.innerHTML = '<div class="cart-empty">Добавьте блюда из меню</div>';
        return;
    }

    elements.cartItems.innerHTML = items.map(item => `
        <div class="cart-item">
            <div>
                <strong>${escapeHtml(item.name)}</strong>
                <span class="cart-item-price">${money.format(Number(item.price) * item.quantity)}</span>
            </div>
            <div class="quantity">
                <button type="button" data-quantity-id="${item.id}" data-delta="-1" aria-label="Уменьшить">−</button>
                <span>${item.quantity}</span>
                <button type="button" data-quantity-id="${item.id}" data-delta="1" aria-label="Увеличить">+</button>
            </div>
        </div>`).join("");

    elements.cartItems.querySelectorAll("[data-quantity-id]").forEach(button => {
        button.addEventListener("click", () => changeQuantity(Number(button.dataset.quantityId), Number(button.dataset.delta)));
    });
}

async function createOrder(event) {
    event.preventDefault();
    if (!state.selectedRestaurant || !state.cart.size) return;

    const form = new FormData(elements.checkoutForm);
    const payload = {
        restaurantId: state.selectedRestaurant.id,
        customerName: String(form.get("customerName") || "").trim(),
        deliveryAddress: String(form.get("deliveryAddress") || "").trim(),
        items: [...state.cart.values()].map(item => ({
            menuItemId: item.id,
            quantity: item.quantity
        }))
    };

    setButtonLoading(elements.orderButton, true, "Оформляем...");
    try {
        state.order = await request("/api/orders", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        state.cart.clear();
        renderCart();
        renderTracking();
        showToast(`Заказ №${state.order.id} создан`);
        document.querySelector("#tracking").scrollIntoView({ behavior: "smooth" });
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        setButtonLoading(elements.orderButton, false, "");
        elements.orderButton.disabled = state.cart.size === 0;
    }
}

function statusIndex(status) {
    return ["CREATED", "IN_TRANSIT", "DELIVERED"].indexOf(status);
}

function renderTracking() {
    if (!state.order) return;

    const order = state.order;
    const delivery = state.delivery;
    const currentIndex = delivery ? statusIndex(delivery.status) : -1;

    elements.trackingCard.innerHTML = `
        <div class="order-summary">
            <div>
                <p class="eyebrow">Заказ №${order.id}</p>
                <h3>${escapeHtml(order.restaurantName)}</h3>
                <p>${escapeHtml(order.customerName)} · ${escapeHtml(order.deliveryAddress)}</p>
                <p>${order.items.map(item => `${escapeHtml(item.name)} × ${item.quantity}`).join(", ")}</p>
            </div>
            <div class="order-amount">${money.format(order.totalAmount)}</div>
        </div>
        ${delivery ? `
            <div class="status-line">
                ${["Принят", "В пути", "Доставлен"].map((label, index) => `
                    <div class="status-step ${index < currentIndex ? "done" : index === currentIndex ? "active" : ""}">${label}</div>`).join("")}
            </div>
            <p class="cart-note">Курьер: ${escapeHtml(delivery.courierName)}</p>
            ${delivery.status !== "DELIVERED" ? `
                <div class="status-actions">
                    <button class="secondary-button" id="next-status-button" type="button">
                        ${delivery.status === "CREATED" ? "Курьер выехал" : "Заказ доставлен"}
                    </button>
                </div>` : ""}
        ` : `
            <form class="courier-form" id="courier-form">
                <input name="courierName" type="text" maxlength="100" placeholder="Имя курьера" required>
                <button class="secondary-button" type="submit">Назначить курьера</button>
            </form>`}
    `;

    document.querySelector("#courier-form")?.addEventListener("submit", createDelivery);
    document.querySelector("#next-status-button")?.addEventListener("click", moveDelivery);
}

async function createDelivery(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const button = event.currentTarget.querySelector("button");
    setButtonLoading(button, true, "Назначаем...");
    try {
        state.delivery = await request("/api/deliveries", {
            method: "POST",
            body: JSON.stringify({
                orderId: state.order.id,
                courierName: String(form.get("courierName") || "").trim()
            })
        });
        renderTracking();
        showToast("Курьер назначен");
    } catch (error) {
        showToast(error.message, "error");
        setButtonLoading(button, false, "");
    }
}

async function moveDelivery(event) {
    const button = event.currentTarget;
    const nextStatus = state.delivery.status === "CREATED" ? "IN_TRANSIT" : "DELIVERED";
    setButtonLoading(button, true, "Обновляем...");
    try {
        state.delivery = await request(`/api/deliveries/${state.delivery.id}/status`, {
            method: "PATCH",
            body: JSON.stringify({ status: nextStatus })
        });
        renderTracking();
        showToast(nextStatus === "IN_TRANSIT" ? "Курьер отправился в путь" : "Заказ доставлен");
    } catch (error) {
        showToast(error.message, "error");
        setButtonLoading(button, false, "");
    }
}

elements.refreshButton.addEventListener("click", loadRestaurants);
elements.clearCart.addEventListener("click", () => {
    state.cart.clear();
    renderCart();
});
elements.checkoutForm.addEventListener("submit", createOrder);

loadRestaurants();
