package app.mildang.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    java.util.List<Payment> findByUserId(String userId);
}
