package com.fitmatch.service;

import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.dto.review.TicketReviewRequest;

/**
 * Câu 17 + 36: đánh giá tách làm hai loại, mở ở hai thời điểm khác nhau.
 *
 * <ul>
 *   <li>Đánh giá PHÒNG GYM neo vào VÉ, mở khi vé đã dùng hết (USED_UP) — khách
 *       chấm điểm cả trải nghiệm, không phải từng buổi lẻ.</li>
 *   <li>Đánh giá PT neo vào BUỔI TẬP, mở ngay khi buổi đó xong (DONE) — người
 *       tập nhớ rõ buổi vừa rồi hơn là nhớ cả gói.</li>
 * </ul>
 *
 * Điểm trung bình vẫn do {@code RatingAggregator} tính trên gym_profile_id /
 * pt_profile_id, nên điểm PT giờ tự động là điểm theo buổi.
 */
public interface TicketReviewService {

    ReviewResponse reviewGym(String customerUsername, Long ticketId, TicketReviewRequest request);

    ReviewResponse reviewPt(String customerUsername, Long sessionId, TicketReviewRequest request);
}
