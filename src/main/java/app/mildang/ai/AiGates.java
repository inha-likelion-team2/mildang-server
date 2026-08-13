package app.mildang.ai;

import java.util.List;

/** AI 반환값 공통 검증 (명세 §15.8.2-3) — 게이트를 통과한 것만 응답에 실린다 */
public final class AiGates {

    /** 흥정·팁·코멘트 공통 금지 어휘 */
    private static final List<String> BANNED = List.of(
            "먹지 마", "먹지마", "참으세", "실패", "포기", "오늘은 접");

    /** 리포트 추가 금지 — 인과 단정·의학 표현 */
    private static final List<String> BANNED_FINDING = List.of(
            "때문", "원인", "유발", "질환", "진단");

    /** 메뉴명 동일성 휴리스틱용 — 다른 메뉴명이 라벨에 등장하면 위반 (§15.4.2 #1) */
    private static final List<String> KNOWN_MENUS = List.of(
            "라면", "김밥", "빵", "떡볶이", "치킨", "칼국수", "삼겹살", "된장찌개", "제육볶음",
            "냉면", "우동", "수제비", "쌀국수", "샐러드", "파스타", "피자", "버거");

    private AiGates() {
    }

    public static boolean clean(String text) {
        return text != null && BANNED.stream().noneMatch(text::contains);
    }

    public static boolean cleanForFinding(String text) {
        return clean(text) && BANNED_FINDING.stream().noneMatch(text::contains);
    }

    public static boolean within(String text, int maxLength) {
        return text != null && !text.isBlank() && text.length() <= maxLength;
    }

    /** label이 target이 아닌 다른 알려진 메뉴명을 담고 있으면 false */
    public static boolean sameMenu(String label, String targetName) {
        if (label == null) {
            return false;
        }
        return KNOWN_MENUS.stream()
                .filter(menu -> !targetName.contains(menu))
                .noneMatch(label::contains);
    }
}
