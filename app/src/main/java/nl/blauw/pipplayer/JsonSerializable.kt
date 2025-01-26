/**
 * 이 인터페이스를 구현하는 클래스는 JSON 문자열로 변환할 수 있는 기능을 제공합니다.
 */
interface JsonSerializable {
    /**
     * 객체를 JSON 형식의 문자열로 변환합니다.
     * @return JSON 문자열
     */
    fun toJsonString(): String
}