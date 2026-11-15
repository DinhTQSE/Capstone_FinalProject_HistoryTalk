# Nâng gói giữa kỳ (Tier Upgrade): API Contract

User đang có gói trả phí còn hạn (Plus/Pro, tháng/năm) có thể nâng lên gói cao hơn ngay giữa kỳ. Phần chưa dùng của gói cũ được quy đổi thành tiền giảm trừ vào giá gói mới. Gói mới bắt đầu từ lúc thanh toán thành công và chạy đủ thời hạn của nó.

Base path: `/Historical-tell/api/v1/payments`

## Quy tắc nâng gói

Hạng gói: `free < plus < pro`, xét theo tên gói, không theo giá.

| Từ | Lên | Kết quả |
|---|---|---|
| Plus tháng | Pro tháng / Pro năm | Cho phép (hạng cao hơn) |
| Plus tháng | Plus năm | Cho phép (cùng hạng, dài hơn) |
| Pro tháng | Pro năm | Cho phép (cùng hạng, dài hơn) |
| Pro (bất kỳ) | Plus (bất kỳ) | Từ chối (hạ hạng) |
| Plus năm | Plus tháng | Từ chối (cùng hạng, ngắn hơn) |
| Cùng gói đang dùng | — | Từ chối |
| Không có gói trả phí active | bất kỳ gói trả phí | Mua bình thường, trả đủ giá |

## Công thức quy đổi

```
value       = số tiền đã trả cho gói hiện tại + tiền quy đổi đã dùng khi mua gói đó
credit      = floor(value × thời_gian_còn_lại / tổng_thời_gian_gói)   // tính tới giây
amountToPay = giá_gói_mới − credit
```

Nếu `credit >= giá_gói_mới` thì hệ thống từ chối và yêu cầu chọn gói dài hơn. Ví dụ: Plus năm còn nửa năm mà muốn lên Pro tháng.

Ví dụ minh hoạ (giá lấy theo số liệu tròn, không phải giá đang bán):

| Ca | Gói cũ | Lúc nâng | credit | Phải trả |
|---|---|---|---|---|
| Plus tháng → Pro tháng | 49.000đ, 30 ngày | còn 15 ngày | 24.500 | 99.000 − 24.500 = 74.500 |
| Plus năm → Pro năm | 499.000đ, 365 ngày | còn 183 ngày | 250.183 | 999.000 − 250.183 = 748.817 |

## Token

- **Ngày nâng gói:** user giữ token gói cũ đã nhận hôm đó và được cộng thêm đủ `limitedToken` của gói mới. Đây là phúc lợi nâng cấp.
- **Từ ngày hôm sau:** top-up hằng ngày chỉ tính theo gói mới, vì gói cũ đã bị tắt khi thanh toán thành công. Dòng gói free (nếu còn active) vẫn được cộng như trước.

## GET `/upgrade-quote?tierId={uuid}`

Xem trước số tiền phải trả, không tạo đơn hàng.

- Auth: Bearer JWT, role `CUSTOMER` hoặc `SCHOOL_ADMIN`.

Response `200`:

```json
{
  "success": true,
  "message": "Upgrade quote calculated successfully",
  "data": {
    "currentTierId": "00000000-0000-0000-0000-000000000002",
    "currentTierTitle": "plus",
    "currentTierNoMonth": 1,
    "currentEndTime": "2026-12-01T00:00",
    "targetTierId": "00000000-0000-0000-0000-000000000003",
    "targetTierTitle": "pro",
    "targetTierNoMonth": 1,
    "originalAmount": 99000,
    "creditAmount": 24500,
    "amountToPay": 74500,
    "upgrade": true
  }
}
```

Khi user không có gói trả phí active: `upgrade = false`, các trường `current*` là `null`, `creditAmount = 0`, `amountToPay = originalAmount`.

## POST `/checkout`

Request không đổi: `{ "tierId": "<uuid>" }`. Response có thêm 2 trường:

| Trường | Ý nghĩa |
|---|---|
| `amount` | Số tiền thực trả trên PayOS (= `originalAmount − upgradeCreditAmount`) |
| `originalAmount` | Giá niêm yết của gói mua |
| `upgradeCreditAmount` | Tiền quy đổi từ gói cũ; `0` nếu là mua mới |

Frontend nên gọi `/upgrade-quote` trước để hiển thị giá. Số tiền lúc checkout có thể lệch vài đồng so với lúc xem trước, vì credit được tính lại theo thời điểm checkout.

## Lỗi `400`

| Message | Khi nào |
|---|---|
| `You can only upgrade to a higher plan (or a longer plan of the same level). Current plan: '<title>'.` | Hạ gói, mua lại gói đang dùng, hoặc gói có tên không thuộc free/plus/pro |
| `Your remaining credit of <credit> VND already covers '<title>'. Please choose a longer plan.` | Tiền quy đổi lớn hơn hoặc bằng giá gói mới |
| `Tier is inactive or deleted` | Gói đích không còn bán |
| `Free tier does not require payment` | Gói đích là free |

## Sau khi thanh toán (webhook PayOS)

Khi webhook đã xác thực báo `PAID`:
1. Tắt mọi gói trả phí đang active của user (`isActive = false`, `endTime` = thời điểm kích hoạt).
2. Tạo gói mới từ thời điểm đó, kéo dài đủ `noMonth` tháng.
3. Cộng `limitedToken` của gói mới vào số dư token.

Gói free không bị tắt.
