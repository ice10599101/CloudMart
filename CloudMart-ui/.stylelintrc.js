module.exports = {
  extends: [require.resolve('@umijs/lint/dist/config/stylelint')],
  rules: {
    // 项目未启用 autoprefixer，手写 -webkit- 前缀以保证渐变色文字、多行省略等跨浏览器兼容
    'property-no-vendor-prefix': null,
    'value-no-vendor-prefix': null,
    // 项目采用紧凑单行声明风格（工具类、keyframe 步骤等），不强制单行仅一条声明
    'declaration-block-single-line-max-declarations': null,
  },
}