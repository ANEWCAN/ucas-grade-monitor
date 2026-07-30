package io.github.ucasscorequery.android;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class ScoreDiffTest {
    @Test
    public void detectsNewAndChangedScores() {
        List<Score> before = Arrays.asList(
                new Score("课程A", "", "80", "2", "否", "秋", ""),
                new Score("课程B", "", "90", "2", "是", "秋", "")
        );
        List<Score> after = Arrays.asList(
                new Score("课程A", "", "85", "2", "否", "秋", ""),
                new Score("课程B", "", "90", "2", "是", "秋", ""),
                new Score("课程C", "", "优秀", "1", "否", "秋", "")
        );

        List<Score> changes = AppPrefs.calculateChanges(before, after);
        assertEquals(2, changes.size());
        assertEquals("课程A", changes.get(0).courseName);
        assertEquals("课程C", changes.get(1).courseName);
    }
}
