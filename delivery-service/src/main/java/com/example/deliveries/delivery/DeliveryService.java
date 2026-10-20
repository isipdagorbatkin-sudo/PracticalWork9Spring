package com.example.deliveries.delivery;
import com.example.deliveries.api.ApiException;
import com.example.deliveries.api.DeliveryDtos.*;
import com.example.deliveries.order.OrderClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private final DeliveryRepository repository;
    private final OrderClient orders;
    public DeliveryService(DeliveryRepository repository,OrderClient orders) { this.repository=repository; this.orders=orders; }
    public DeliveryView create(CreateDelivery request) {
        if (repository.existsByOrderId(request.orderId())) throw duplicate();
        var order=orders.get(request.orderId());
        try {
            Delivery delivery=repository.saveAndFlush(new Delivery(order.id(),request.courierName(),order.deliveryAddress()));
            log.info("Delivery created: deliveryId={}, orderId={}", delivery.getId(), order.id());
            return DeliveryView.from(delivery);
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
    }
    @Transactional(readOnly=true)
    public List<DeliveryView> list() { return repository.findAllByOrderByIdDesc().stream().map(DeliveryView::from).toList(); }
    @Transactional(readOnly=true)
    public DeliveryView get(Long id) { return DeliveryView.from(require(id)); }
    @Transactional(readOnly=true)
    public DeliveryView byOrder(Long orderId) { return DeliveryView.from(repository.findByOrderId(orderId).orElseThrow(DeliveryService::notFound)); }
    @Transactional
    public DeliveryView changeStatus(Long id,DeliveryStatus next) {
        Delivery delivery=require(id);
        DeliveryStatus current=delivery.getStatus();
        if (current==next) return DeliveryView.from(delivery);
        if (!((current==DeliveryStatus.CREATED && next==DeliveryStatus.IN_TRANSIT)
                || (current==DeliveryStatus.IN_TRANSIT && next==DeliveryStatus.DELIVERED))) {
            throw new ApiException(HttpStatus.CONFLICT,"INVALID_DELIVERY_TRANSITION","Разрешены переходы CREATED → IN_TRANSIT → DELIVERED");
        }
        delivery.changeStatus(next);
        repository.flush();
        log.info("Delivery status changed: deliveryId={}, previousStatus={}, currentStatus={}", id, current, next);
        return DeliveryView.from(delivery);
    }
    private Delivery require(Long id) { return repository.findById(id).orElseThrow(DeliveryService::notFound); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"DELIVERY_NOT_FOUND","Доставка не найдена"); }
    private static ApiException duplicate() { return new ApiException(HttpStatus.CONFLICT,"DELIVERY_ALREADY_EXISTS","Для этого заказа доставка уже создана"); }
}
