import type {
  ResumeDraftContent,
  ResumeEducation,
  ResumeExperience,
  ResumeProject,
  ResumeSkillGroup,
  ResumeStyle
} from '@/api/resumeDraft'

interface RenderOptions {
  placeholder?: boolean
}

export function defaultResumeStyle(): ResumeStyle {
  return {
    bodyFontSize: 10.5,
    nameFontSize: 23,
    sectionTitleFontSize: 12.5,
    entryTitleFontSize: 10.8,
    metaFontSize: 9.8,
    lineHeight: 1.55,
    letterSpacing: 0,
    sectionFontSizes: {}
  }
}

function resolveResumeStyle(style?: ResumeStyle): ResumeStyle {
  const base = defaultResumeStyle()
  if (!style) return base
  return {
    bodyFontSize: positive(style.bodyFontSize, base.bodyFontSize),
    nameFontSize: positive(style.nameFontSize, base.nameFontSize),
    sectionTitleFontSize: positive(style.sectionTitleFontSize, base.sectionTitleFontSize),
    entryTitleFontSize: positive(style.entryTitleFontSize, base.entryTitleFontSize),
    metaFontSize: positive(style.metaFontSize, base.metaFontSize),
    lineHeight: positive(style.lineHeight, base.lineHeight),
    letterSpacing: style.letterSpacing >= 0 ? style.letterSpacing : 0,
    sectionFontSizes: style.sectionFontSizes ?? {}
  }
}

function positive(value: number | undefined, fallback: number): number {
  return value && value > 0 ? value : fallback
}

function styleVariables(style: ResumeStyle): string {
  return [
    `--body-size:${style.bodyFontSize}pt`,
    `--name-size:${style.nameFontSize}pt`,
    `--section-title-size:${style.sectionTitleFontSize}pt`,
    `--entry-title-size:${style.entryTitleFontSize}pt`,
    `--meta-size:${style.metaFontSize}pt`,
    `--line-height:${style.lineHeight}`,
    `--letter-spacing:${style.letterSpacing}pt`
  ].join(';')
}

export function emptyResumeContent(): ResumeDraftContent {
  return {
    personal: {
      name: '',
      gender: '',
      birthDate: '',
      ethnicity: '',
      politicalStatus: '',
      birthplace: '',
      currentCity: '',
      phone: '',
      email: '',
      communityUrl: '',
      photo: ''
    },
    education: [],
    internships: [],
    projects: [],
    skills: [],
    awards: [],
    style: defaultResumeStyle()
  }
}

export function resumeFileBaseName(content: ResumeDraftContent): string {
  const name = content.personal?.name?.trim()
  return sanitizeFileName(name || '我的简历')
}

export function renderResumeDocument(
  content: ResumeDraftContent,
  options: RenderOptions = {}
): string {
  const placeholder = options.placeholder === true
  const style = resolveResumeStyle(content.style)
  const personal = content.personal ?? emptyResumeContent().personal
  const name = text(personal.name) || (placeholder ? '你的姓名' : '')
  const firstLine = [
    text(personal.gender),
    text(personal.birthDate),
    text(personal.ethnicity),
    text(personal.politicalStatus),
    labeled('出生地', personal.birthplace),
    labeled('现居', personal.currentCity)
  ].filter(Boolean)
  const secondLine = [
    labeled('电话', personal.phone),
    labeled('邮箱', personal.email),
    labeled('社区', personal.communityUrl)
  ].filter(Boolean)
  const educationSummary = educationHeadline(content.education)
  const hasContent = Boolean(
    name ||
      firstLine.length ||
      secondLine.length ||
      educationSummary ||
      content.education?.length ||
      content.internships?.length ||
      content.projects?.length ||
      content.skills?.length ||
      content.awards?.length
  )

  const sections = [
    renderEducation(content.education, style),
    renderExperienceSections(content.internships, style),
    renderProjects(content.projects, style),
    renderSkills(content.skills, style),
    renderAwards(content.awards, style)
  ].filter(Boolean)

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${escapeHtml(name || '简历')}</title>
<style>:root{${styleVariables(style)}}${RESUME_CSS}</style>
</head>
<body>
<div class="page">
  <div class="page-content">
  <header class="resume-header">
    <div class="header-main">
      <h1 class="name">${escapeHtml(name)}</h1>
      ${firstLine.length ? `<p class="meta">${firstLine.map(escapeHtml).join('<span class="sep">|</span>')}</p>` : ''}
      ${secondLine.length ? `<p class="meta">${secondLine.map(escapeHtml).join('<span class="sep">|</span>')}</p>` : ''}
      ${educationSummary ? `<p class="meta">${escapeHtml(educationSummary)}</p>` : ''}
    </div>
    ${renderPhoto(personal.photo, placeholder)}
  </header>
  ${sections.join('\n')}
  ${placeholder && !hasContent ? '<div class="empty-hint">填写左侧信息后，这里会实时生成简历</div>' : ''}
  </div>
</div>
<script>
(function () {
  function fitOnePage() {
    var page = document.querySelector('.page');
    var content = document.querySelector('.page-content');
    if (!page || !content) return;
    content.style.zoom = '1';
    var style = window.getComputedStyle(page);
    var paddingTop = parseFloat(style.paddingTop) || 0;
    var paddingBottom = parseFloat(style.paddingBottom) || 0;
    var pageHeight = 297 * 96 / 25.4;
    var available = pageHeight - paddingTop - paddingBottom - 2;
    var needed = content.scrollHeight;
    if (needed > available) {
      var scale = Math.max(0.68, available / needed);
      content.style.zoom = String(scale);
    }
  }
  if (document.readyState === 'complete') {
    fitOnePage();
  } else {
    window.addEventListener('load', fitOnePage);
  }
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(fitOnePage);
  }
  setTimeout(fitOnePage, 300);
})();
</script>
</body>
</html>`
}

const RESUME_CSS = `
@page { size: A4; margin: 0; }
* { box-sizing: border-box; }
html, body {
  margin: 0;
  padding: 0;
  background: #fff;
  color: #222;
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Source Han Sans SC", sans-serif;
  font-size: var(--body-size);
  line-height: var(--line-height);
  letter-spacing: var(--letter-spacing);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}
.page {
  width: 210mm;
  min-height: 297mm;
  padding: 12mm 15mm 11mm;
  margin: 0 auto;
  background: #fff;
  position: relative;
}
.page-content {
  width: 100%;
}
.resume-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8mm;
}
.header-main { flex: 1; min-width: 0; }
.name {
  margin: 0 0 3.2mm;
  color: #1F4E79;
  font-size: var(--name-size);
  line-height: 1.15;
  font-weight: 700;
  letter-spacing: 0.06em;
}
.meta {
  margin: 0 0 0.8mm;
  color: #333;
  font-size: var(--meta-size);
  line-height: var(--line-height);
}
.sep { color: #9aa4ae; margin: 0 1.5mm; }
.photo {
  width: 26mm;
  height: 34mm;
  object-fit: cover;
  flex: none;
  background: #f2f5f8;
}
.photo-placeholder {
  width: 26mm;
  height: 34mm;
  flex: none;
  border: 0.4mm dashed #c7d2dd;
  color: #a0acb9;
  font-size: 9pt;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f9fb;
}
.section {
  margin-top: 5.2mm;
  font-size: var(--section-body-size, var(--body-size));
}
.section-title {
  margin: 0 0 2.6mm;
  padding-bottom: 1.2mm;
  color: #1F4E79;
  font-size: var(--section-title-size);
  font-weight: 700;
  letter-spacing: var(--letter-spacing);
  border-bottom: 0.45mm solid #1F4E79;
}
.entry { margin-bottom: 3.4mm; }
.entry:last-child { margin-bottom: 0; }
.entry-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6mm;
}
.entry-title {
  color: #222;
  font-size: var(--entry-title-size);
  font-weight: 700;
  line-height: var(--line-height);
}
.entry-date {
  color: #1F4E79;
  font-size: calc(var(--entry-title-size) * 0.95);
  font-weight: 700;
  white-space: nowrap;
}
.entry-sub {
  margin-top: 0.7mm;
  color: #555;
  font-size: calc(var(--section-body-size, var(--body-size)) * 0.92);
  line-height: var(--line-height);
}
.entry-line {
  margin-top: 0.8mm;
  color: #333;
  font-size: var(--section-body-size, var(--body-size));
  line-height: var(--line-height);
}
.entry-line b { color: #222; }
.entry-tech {
  margin-top: 0.7mm;
  color: #666;
  font-size: calc(var(--section-body-size, var(--body-size)) * 0.9);
  line-height: var(--line-height);
}
.entry-repo {
  margin-top: 0.4mm;
  color: #2e75b6;
  font-size: calc(var(--section-body-size, var(--body-size)) * 0.9);
  line-height: var(--line-height);
  word-break: break-all;
}
ul.bullets {
  margin: 1mm 0 0;
  padding-left: 4.5mm;
}
ul.bullets li {
  margin: 0.6mm 0;
  padding-left: 0.4mm;
  font-size: var(--section-body-size, var(--body-size));
  line-height: var(--line-height);
}
ul.bullets li::marker { color: #333; }
.skills { margin: 0; padding: 0; list-style: none; }
.skills li {
  position: relative;
  margin: 0.9mm 0;
  padding-left: 4.2mm;
  font-size: var(--section-body-size, var(--body-size));
  line-height: var(--line-height);
}
.skills li::before {
  content: "•";
  position: absolute;
  left: 0;
  top: 0;
  color: #333;
}
.skill-label { font-weight: 700; color: #222; }
.empty-hint {
  margin-top: 16mm;
  padding: 10mm;
  border: 0.4mm dashed #d5dee8;
  color: #9aa7b5;
  text-align: center;
  font-size: var(--body-size);
}
`

function renderPhoto(photo: string | undefined, placeholder: boolean): string {
  if (photo && /^data:image\/(png|jpe?g|webp);base64,/i.test(photo)) {
    return `<img class="photo" src="${escapeHtml(photo)}" alt="证件照" />`
  }
  return placeholder ? '<div class="photo-placeholder">证件照</div>' : ''
}

function renderEducation(items: ResumeEducation[] | undefined, style: ResumeStyle): string {
  const list = (items ?? []).filter(hasEducation)
  if (!list.length) return ''
  const entries = list.map((item) => {
    const title = [item.school, item.college].map(text).filter(Boolean).join('  ')
    const major = [item.major, item.degree].map(text).filter(Boolean).join(' ')
    const headline = title && major ? `${title} · ${major}` : title || major
    const gpa = text(item.gpa)
    const ranking = text(item.ranking)
    return `<div class="entry">
      ${renderEntryHead(headline, dateRange(item.startDate, item.endDate))}
      ${text(item.courses) ? `<p class="entry-line"><b>核心课程：</b>${escapeHtml(text(item.courses))}</p>` : ''}
      ${gpa || ranking ? `<p class="entry-line">${gpa ? `<b>GPA：</b>${escapeHtml(gpa)}` : ''}${gpa && ranking ? '  |  ' : ''}${ranking ? `<b>排名：</b>${escapeHtml(ranking)}` : ''}</p>` : ''}
    </div>`
  })
  return section('教育背景', entries.join(''), 'education', style)
}

function renderExperienceSections(items: ResumeExperience[] | undefined, style: ResumeStyle): string {
  const list = (items ?? []).filter(hasExperience)
  if (!list.length) return ''
  const work = list.filter(isWorkExperience)
  const internships = list.filter((item) => !isWorkExperience(item))
  return [
    work.length ? renderExperienceSection('工作经历', work, style) : '',
    internships.length ? renderExperienceSection('实习经历', internships, style) : ''
  ].filter(Boolean).join('\n')
}

function renderExperienceSection(title: string, list: ResumeExperience[], style: ResumeStyle): string {
  const entries = list.map((item) => `<div class="entry">
    ${renderEntryHead(
      [text(item.company), text(item.role)].filter(Boolean).join(' · '),
      dateRange(item.startDate, item.endDate)
    )}
    ${text(item.techStack) ? `<p class="entry-tech">${escapeHtml(text(item.techStack))}</p>` : ''}
    ${renderBullets(item.bullets)}
  </div>`)
  return section(title, entries.join(''), 'experience', style)
}

function isWorkExperience(item: ResumeExperience): boolean {
  return item.type === 'WORK'
}

function renderProjects(items: ResumeProject[] | undefined, style: ResumeStyle): string {
  const list = (items ?? []).filter(hasProject)
  if (!list.length) return ''
  const entries = list.map((item) => `<div class="entry">
    ${renderEntryHead(text(item.name), dateRange(item.startDate, item.endDate))}
    ${text(item.techStack) ? `<p class="entry-tech">${escapeHtml(text(item.techStack))}</p>` : ''}
    ${text(item.repoUrl) ? `<p class="entry-repo">仓库：${escapeHtml(text(item.repoUrl))}</p>` : ''}
    ${renderBullets(item.bullets)}
  </div>`)
  return section('项目经历', entries.join(''), 'projects', style)
}

function renderSkills(items: ResumeSkillGroup[] | undefined, style: ResumeStyle): string {
  const list = (items ?? []).filter((item) => text(item.label) || text(item.content))
  if (!list.length) return ''
  const rows = list.map((item) => `<li>${text(item.label) ? `<span class="skill-label">${escapeHtml(text(item.label))}：</span>` : ''}${escapeHtml(text(item.content))}</li>`)
  return section('专业技能', `<ul class="skills">${rows.join('')}</ul>`, 'skills', style)
}

function renderAwards(items: string[] | undefined, style: ResumeStyle): string {
  const list = (items ?? []).map(text).filter(Boolean)
  if (!list.length) return ''
  return section('荣誉奖项', renderBullets(list), 'awards', style)
}

function section(title: string, body: string, key: string, style: ResumeStyle): string {
  const size = style.sectionFontSizes?.[key]
  const sizeStyle = size && size > 0 ? ` style="--section-body-size:${size}pt"` : ''
  return `<section class="section section-${key}"${sizeStyle}><h2 class="section-title">${escapeHtml(title)}</h2>${body}</section>`
}

function renderEntryHead(title: string, date: string): string {
  return `<div class="entry-head">
    <span class="entry-title">${escapeHtml(title)}</span>
    ${date ? `<span class="entry-date">${escapeHtml(date)}</span>` : ''}
  </div>`
}

function renderBullets(items: string[] | undefined): string {
  const list = (items ?? []).map(text).filter(Boolean)
  if (!list.length) return ''
  return `<ul class="bullets">${list.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`
}

function educationHeadline(items: ResumeEducation[] | undefined): string {
  const first = (items ?? []).find((item) => text(item.school))
  if (!first) return ''
  const major = [first.major, first.degree].map(text).filter(Boolean).join(' ')
  let headline = text(first.school)
  if (major) headline += ` · ${major}`
  const year = text(first.endDate).match(/\d{4}/)?.[0]
  if (year) headline += `（${year} 届）`
  return headline
}

function dateRange(start?: string, end?: string): string {
  const from = text(start)
  const to = text(end)
  if (from && to) return `${from} - ${to}`
  return from || to
}

function hasEducation(item: ResumeEducation): boolean {
  return Boolean(text(item.school) || text(item.college) || text(item.major) || text(item.degree))
}

function hasExperience(item: ResumeExperience): boolean {
  return Boolean(text(item.company) || text(item.role) || text(item.techStack) || item.bullets?.some(text))
}

function hasProject(item: ResumeProject): boolean {
  return Boolean(text(item.name) || text(item.techStack) || text(item.repoUrl) || item.bullets?.some(text))
}

function labeled(label: string, value?: string): string {
  const content = text(value)
  return content ? `${label}：${content}` : ''
}

function text(value?: string | null): string {
  return value?.trim() ?? ''
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function sanitizeFileName(value: string): string {
  return value.replace(/[\\/:*?"<>|\r\n]/g, '_').trim() || '我的简历'
}
