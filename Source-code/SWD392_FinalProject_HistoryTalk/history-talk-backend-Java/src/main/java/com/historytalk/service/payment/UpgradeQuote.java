package com.historytalk.service.payment;

import com.historytalk.entity.payment.UserTier;

/**
 * Price breakdown for buying a tier, optionally replacing the user's active paid subscription.
 *
 * @param replacedSubscription the active paid subscription being upgraded, or null for a plain purchase
 * @param originalAmount       list price of the target tier (VND)
 * @param creditAmount         prorated value of the unused part of the replaced subscription (VND)
 * @param amountToPay          what the user actually pays: originalAmount - creditAmount (VND)
 */
public record UpgradeQuote(UserTier replacedSubscription, int originalAmount, int creditAmount, int amountToPay) {
}
