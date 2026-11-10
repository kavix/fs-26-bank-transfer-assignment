package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;
    private final CustomerRepository customerRepository;
    private final BankTransactionRepository bankTransactionRepository;

    @GetMapping("/")
    public String dashboard(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            model.addAttribute("customers", customerRepository.searchByName(keyword.trim()));
        } else {
            model.addAttribute("customers", customerRepository.findAllOrdered());
        }

        BigDecimal totalBalance = customerRepository.getTotalBalance();

        model.addAttribute("allCustomers", customerRepository.findAllOrdered());
        model.addAttribute("transferRequest", new TransferRequest());
        model.addAttribute("customer", new Customer());
        model.addAttribute("newCustomer", new Customer());
        model.addAttribute("totalCustomers", customerRepository.countCustomersNative());
        model.addAttribute("totalBalance", totalBalance != null ? totalBalance : BigDecimal.ZERO);
        model.addAttribute("successCount", bankTransactionRepository.countByStatus(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankTransactionRepository.countByStatus(TransactionStatus.FAILED));
        model.addAttribute("transactions", bankTransactionRepository.findAllLatest());
        model.addAttribute("keyword", keyword != null ? keyword : "");

        return "bank";
    }

    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .findFirst()
                    .orElse("Validation error in customer form");
            redirectAttributes.addFlashAttribute("error", errorMsg);
            return "redirect:/";
        }

        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success", "Customer " + customer.getFullName() + " created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .findFirst()
                    .orElse("Validation error in transfer form");
            redirectAttributes.addFlashAttribute("error", errorMsg);
            return "redirect:/";
        }

        try {
            bankService.transfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.isSimulateFailure()
            );
            redirectAttributes.addFlashAttribute("success", "Transfer completed successfully!");
        } catch (Exception e) {
            try {
                auditService.recordFailure(
                        request.getFromAccount(),
                        request.getToAccount(),
                        request.getAmount(),
                        e.getMessage()
                );
            } catch (Exception auditEx) {
                // Ignore audit failure to let main error surface
            }
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable("accountNumber") String accountNumber, Model model) {
        Customer customer = customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        BigDecimal totalSent = bankTransactionRepository.getTotalSent(accountNumber);

        model.addAttribute("customer", customer);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("transactions", bankTransactionRepository.findHistory(accountNumber));
        model.addAttribute("totalSent", totalSent != null ? totalSent : BigDecimal.ZERO);

        return "history";
    }

    // ===== Task 12 Extensions =====

    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(value = "min", defaultValue = "10000") BigDecimal min, Model model) {
        model.addAttribute("transfers", bankTransactionRepository.findLargeTransfers(min));
        model.addAttribute("min", min);
        return "large-transfers";
    }

    @GetMapping("/deposit")
    public String showDepositForm(Model model) {
        model.addAttribute("customers", customerRepository.findAllOrdered());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam("accountNumber") String accountNumber,
                          @RequestParam("amount") BigDecimal amount,
                          RedirectAttributes redirectAttributes) {
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "Deposited LKR " + amount + " to account " + accountNumber + " successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Deposit failed: " + e.getMessage());
        }
        return "redirect:/deposit";
    }

    @GetMapping("/withdraw")
    public String showWithdrawForm(Model model) {
        model.addAttribute("customers", customerRepository.findAllOrdered());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam("accountNumber") String accountNumber,
                           @RequestParam("amount") BigDecimal amount,
                           RedirectAttributes redirectAttributes) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "Withdrew LKR " + amount + " from account " + accountNumber + " successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Withdrawal failed: " + e.getMessage());
        }
        return "redirect:/withdraw";
    }
}
