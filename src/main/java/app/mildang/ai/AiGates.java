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

    /**
     * 기준 수량으로 인정하는 표현 (§8 #12). 숫자+단위(「1인분」「2쪽」)나 우리말 수량(「한 그릇」「반 봉지」).
     * 「밀가루가 들어갑니다」처럼 무엇 얼마만큼인지 없는 근거를 걸러내는 게 목적이다.
     */
    private static final java.util.regex.Pattern QUANTITY = java.util.regex.Pattern.compile(
            "(\\d+(\\.\\d+)?\\s*(인분|봉지|봉|그릇|개|장|조각|쪽|컵|잔|마리|판|줄|접시|대접|스푼|g|ml|kg|L))"
                    + "|((한|두|세|네|다섯|반)\\s*(인분|봉지|봉|그릇|개|장|조각|쪽|컵|잔|마리|판|줄|접시|대접))");

    private AiGates() {
    }

    /**
     * §8 #12 — 추정의 <b>기준 수량</b>이 명시됐는지. {@code unit}이 있고, 근거 한 줄에 수량 표현이
     * 하나라도 있어야 한다("면 전체가 밀 — 봉지라면 1인분 기준").
     *
     * <p>이게 없으면 사용자가 80이라는 숫자를 신뢰할 근거가 사라진다. 한 봉지인지 두 봉지인지 모르면
     * 흥정의 출발점(「절반으로 줄이자」가 무엇의 절반인지)도 정의되지 않는다.
     */
    public static boolean hasQuantityBasis(String unit, String basis) {
        if (unit == null || unit.isBlank()) {
            return false;
        }
        // 단위 자체가 수량을 담고 있으면(「1봉지」) 그것으로 충분하다
        return hasQuantity(unit) || hasQuantity(basis);
    }

    private static boolean hasQuantity(String text) {
        return text != null && QUANTITY.matcher(text).find();
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
