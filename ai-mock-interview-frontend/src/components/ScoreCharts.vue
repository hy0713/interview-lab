<script setup>
import { computed } from 'vue'
import { categoryLabels, date } from '../labels'
const props = defineProps({
  trend: { type: Array, default: () => [] },
  showTrend: { type: Boolean, default: true },
  abilities: { type: Array, default: () => [] }
})
const points = computed(() =>
  props.trend.map((r, i) => ({
    x: 40 + (i * 520) / Math.max(1, props.trend.length - 1),
    y: 160 - r.total_score * 1.3,
    row: r
  }))
)
</script>
<template>
  <div class="comparison-grid mb" :class="{ 'single-chart': !showTrend }">
    <section v-if="showTrend" class="panel">
      <h3>历史成绩趋势</h3>
      <el-empty v-if="!trend.length" description="完成面试后显示成长记录" :image-size="60" />
      <template v-else
        ><svg
          class="trend-chart"
          viewBox="0 0 600 205"
          role="img"
          aria-label="最近完成面试的百分制成绩趋势"
        >
          <g v-for="value in [0, 50, 100]" :key="value">
            <line
              x1="40"
              x2="565"
              :y1="160 - value * 1.3"
              :y2="160 - value * 1.3"
              stroke="#e2e7dc"
            />
            <text x="6" :y="165 - value * 1.3">{{ value }}</text>
          </g>
          <polyline
            :points="points.map((p) => p.x + ',' + p.y).join(' ')"
            fill="none"
            stroke="#36776c"
            stroke-width="3"
          />
          <g v-for="p in points" :key="p.row.id">
            <circle :cx="p.x" :cy="p.y" r="5" fill="#36776c">
              <title>
                {{ date(p.row.finished_at) }} · {{ p.row.position }} · {{ p.row.total_score }} 分
              </title>
            </circle>
            <text v-if="trend.length <= 10" :x="p.x" :y="p.y - 12" text-anchor="middle">
              {{ p.row.total_score }}
            </text>
          </g>
          <text x="40" y="195">{{ date(trend[0].finished_at).slice(0, 10) }}</text>
          <text x="560" y="195" text-anchor="end">
            {{ date(trend.at(-1).finished_at).slice(0, 10) }}
          </text>
        </svg>
        <p class="muted">
          最近 {{ trend.length }} 场已完成面试；可按岗位、难度筛选后比较。
        </p></template
      >
    </section>
    <section class="panel">
      <h3>按题型查看表现</h3>
      <el-empty v-if="!abilities.length" description="暂无评分样本" :image-size="60" />
      <div v-for="item in abilities" :key="item.name" class="ability-row">
        <div>
          {{ categoryLabels[item.name] || item.name }} <b>{{ item.score }} 分</b>
        </div>
        <el-progress :percentage="item.score" :show-text="false" color="#36776c" /><small
          class="muted"
          >{{ item.samples }} 次作答，展示题型均分</small
        >
      </div>
    </section>
  </div>
</template>
