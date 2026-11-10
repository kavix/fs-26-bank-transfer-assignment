package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Inserts sample customers the first time the application starts (empty database). */
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) throws Exception {
        if (customerRepository.count() == 0) {
            Customer c1 = new Customer("100001", "Kamal", "kamal@example.com", new BigDecimal("50000.00"));
            Customer c2 = new Customer("100002", "Nimali", "nimali@example.com", new BigDecimal("25000.00"));
            Customer c3 = new Customer("100003", "Saman", "saman@example.com", new BigDecimal("10000.00"));
            Customer bankReserve = new Customer("999999", "Bank Reserve", "bank@example.com", new BigDecimal("0.00"));

            customerRepository.save(c1);
            customerRepository.save(c2);
            customerRepository.save(c3);
            customerRepository.save(bankReserve);

            System.out.println(">>> Sample data loaded successfully! Total bank money: LKR 85,000.00 <<<");
        }
    }
}
