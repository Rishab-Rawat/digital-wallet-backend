package com.example.wallet.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}
    public record CreateUserRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank String phone) {}
    public record CreateWalletRequest(@NotNull Long userId, @NotBlank @Size(max=120) String name) {}
    public record TransferRequest(@NotNull Long fromWalletId, @NotNull Long toWalletId, @Min(1) long amountPaise, String note) {}
    public record TopUpRequest(@NotNull Long walletId, @Min(1) long amountPaise, String note) {}
    public record WithdrawRequest(@NotNull Long walletId, @Min(1) long amountPaise, String note) {}
    public record TransferResponse(long transferId, String status, long amountPaise, String currency, boolean riskFlag, String message, Instant createdAt) {}
    public record BalanceResponse(long accountId, Long userId, String name, long balancePaise, String currency) {}
    public record StatementEntry(long ledgerEntryId, long transferId, long accountId, String direction, long amountPaise, String transferType, String transferStatus, String note, Instant createdAt) {}
    public record PageResponse<T>(List<T> items, int page, int size, long total) {}
    public record AskRequest(@NotBlank String question) {}
    public record AskResponse(String answer, long amountPaise, List<Long> transferIds, String interpretation) {}
    public record ReconciliationResponse(long runId, String status, int checksRun, int problemsFound, String details) {}
    public record ErrorResponse(String code, String message, String requestId) {}
    public record ReviewRequest(@NotBlank String action, @NotBlank String changedBy, String reason) {}
}
