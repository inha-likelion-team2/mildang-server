package app.mildang.challenge;

import java.util.Map;

/**
 * 화면 2 한줄 코멘트(startTip) — AI 미사용, 3문항 27조합 하드코딩 (2026-08-11 와이어프레임 결정).
 * 키: 면|빵|간식 (L=0-1, M=2-3, H=4+). 규칙: 2문장·90자 이내·전략 제시형·금지 어휘 없음.
 */
public final class StartTips {

    private static final Map<String, String> TIPS = Map.ofEntries(
            Map.entry("L|L|L", "밀가루와 이미 거리가 있는 편이네요. 이번 판은 기록 습관만 붙여도 완주예요."),
            Map.entry("L|L|M", "과자·간식이 유일한 변수네요. 밤 시간대에만 예산을 아껴두면 게임이 쉬워져요."),
            Map.entry("L|L|H", "식사는 깨끗한데 간식이 주전장이네요. 하루 한 번, 가장 아쉬운 간식만 골라 드세요."),
            Map.entry("L|M|L", "빵이 가끔 등장하는 정도예요. 아침 빵 한 번을 반쪽으로 줄이면 여유가 커져요."),
            Map.entry("L|M|M", "빵과 간식이 반반이네요. 이왕이면 더 만족스러운 쪽에 예산을 몰아주세요."),
            Map.entry("L|M|H", "간식 비중이 커요. 식사보다 간식에서 깎는 게 체감이 적습니다."),
            Map.entry("L|H|L", "빵이 주식에 가깝네요. 통째로 줄이기보다 반쪽+커피 조합으로 단가를 낮춰보세요."),
            Map.entry("L|H|M", "빵 중심 식단이에요. 가장 자주 가는 빵집 메뉴 하나만 단골 흥정 대상으로 정해두세요."),
            Map.entry("L|H|H", "빵과 간식이 큰손이네요. 식사 빵은 지키고 간식 빵부터 줄이는 순서를 추천해요."),
            Map.entry("M|L|L", "면이 가끔인 정도라 여유 있어요. 면 먹는 날을 미리 정해두면 예산이 남습니다."),
            Map.entry("M|L|M", "면과 간식이 번갈아 나오네요. 같은 날에 겹치지 않게만 배치해도 충분해요."),
            Map.entry("M|L|H", "간식이 면보다 잦네요. 면은 즐기고, 야식 간식만 반으로 흥정해보세요."),
            Map.entry("M|M|L", "면과 빵이 고르게 있는 패턴이에요. 주중 하루만 0짜리 식단으로 만들면 페이스가 편해져요."),
            Map.entry("M|M|M", "골고루 드시는 균형형이네요. 전부 줄이기보다 요일별로 하나씩만 협상 대상으로 잡으세요."),
            Map.entry("M|M|H", "간식이 제일 큰 지출처예요. 식사는 그대로 두고 간식 단가부터 깎는 게 순서입니다."),
            Map.entry("M|H|L", "빵이 중심, 면이 조연이네요. 빵 반쪽 전략만 익혀도 예산 절반이 지켜져요."),
            Map.entry("M|H|M", "빵 비중이 커요. 면 먹는 날엔 빵을 쉬는 식으로 하루 하나 원칙을 시도해보세요."),
            Map.entry("M|H|H", "빵과 간식이 매일 등장할 기세네요. 아침 빵과 밤 간식 중 하나만 남기는 협상이 핵심이에요."),
            Map.entry("H|L|L", "면 사랑이 확실하네요. 횟수는 지키고 한 번의 양만 줄이는 게 가장 아프지 않아요."),
            Map.entry("H|L|M", "면이 주식이군요. 라면 한 번을 반봉지+계란으로 바꾸는 것부터 시작해보세요."),
            Map.entry("H|L|H", "면과 야식 간식 조합이네요. 밤 라면만 반으로 줄여도 주간 예산의 반이 돌아와요."),
            Map.entry("H|M|L", "면 위주에 빵이 간간이 있네요. 면은 양 조절, 빵은 요일 지정으로 나눠 관리하면 편해요."),
            Map.entry("H|M|M", "면·빵·간식이 다 있는 풀코스네요. 가장 만만한 한 번만 정해서 거기서만 깎으세요."),
            Map.entry("H|M|H", "면과 간식이 모두 잦아요. 하나를 0으로 만들기보다 둘 다 3분의 2로 줄이는 쪽이 오래갑니다."),
            Map.entry("H|H|L", "면과 빵이 모두 주식급이네요. 한 끼에 둘이 같이 나오는 날만 피해도 큰 차이가 나요."),
            Map.entry("H|H|M", "면·빵 비중이 높아요. 점심 면, 아침 빵 중 더 아까운 쪽을 흥정 단골로 정하세요."),
            Map.entry("H|H|H", "전 종목 풀출전이네요. 첫 주는 줄이기보다 기록으로 내 패턴을 아는 데 쓰는 게 남는 장사예요."));

    private StartTips() {
    }

    public static String of(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack) {
        return TIPS.get(key(noodle) + "|" + key(bread) + "|" + key(snack));
    }

    private static String key(SurveyLevel level) {
        return switch (level) {
            case LOW -> "L";
            case MID -> "M";
            case HIGH -> "H";
        };
    }
}
