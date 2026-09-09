<template>
  <view class="content">
    <view class="row b-b">
      <text class="tit">姓名</text>
      <input
        class="input"
        type="text"
        v-model="addressData.name"
        placeholder="收货人姓名"
        placeholder-class="placeholder"
      />
    </view>
    <view class="row b-b">
      <text class="tit">手机号码</text>
      <input
        class="input"
        type="number"
        v-model="addressData.phoneNumber"
        placeholder="收货人手机号码"
        placeholder-class="placeholder"
      />
    </view>
    <view class="row b-b">
      <text class="tit">所在区域</text>
      <picker
        class="region-picker"
        mode="multiSelector"
        :range="regionColumns"
        :value="regionIndex"
        @columnchange="handleRegionColumnChange"
        @change="handleRegionChange"
      >
        <view class="region-picker-content">
          <text v-if="selectedRegion" class="region-value">{{ selectedRegion }}</text>
          <text v-else class="region-placeholder">请选择省/市/区</text>
          <text class="region-arrow">›</text>
        </view>
      </picker>
    </view>
    <view class="row b-b">
      <text class="tit">详细地址</text>
      <input
        class="input"
        type="text"
        v-model="addressData.detailAddress"
        placeholder="详细地址"
        placeholder-class="placeholder"
      />
    </view>

    <view class="row default-row">
      <text class="tit">设为默认</text>
      <switch
        :checked="addressData.defaultStatus === 1"
        color="#fa436a"
        @change="handleSwitchChange"
      />
    </view>
    <button class="add-btn" @click="handleConfirm">提交</button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { addAddressAPI, updateAddressAPI, fetchAddressDetailAPI } from '@/apis/address'
import type { MemberReceiveAddress } from '@/types/address'
import { city as cityData, county as countyData, province as provinceData } from 'china-region-data'

// ===== 页面数据 =====
// 操作类型（add/edit）
const manageType = ref('')
// 地址表单数据
const addressData = ref<MemberReceiveAddress>({
  name: '',
  phoneNumber: '',
  detailAddress: '',
  defaultStatus: 0,
  province: '',
  city: '',
  region: '',
})

// 全国真实省、市、区县数据。H5 端使用 multiSelector，避免 mode="region" 在部分浏览器中弹窗为空。
const provinceOptions = provinceData
const cityOptions = ref(cityData[provinceOptions[0]?.id] || [])
const countyOptions = ref(countyData[cityOptions.value[0]?.id] || [])
const regionIndex = ref([0, 0, 0])
const regionColumns = computed(() => [
  provinceOptions.map((item) => item.name),
  cityOptions.value.map((item) => item.name),
  countyOptions.value.map((item) => item.name),
])

const selectedRegion = computed(() =>
  [addressData.value.province, addressData.value.city, addressData.value.region]
    .filter(Boolean)
    .join(' '),
)

const syncRegionPicker = () => {
  const provinceIndex = Math.max(
    provinceOptions.findIndex((item) => item.name === addressData.value.province),
    0,
  )
  const selectedProvince = provinceOptions[provinceIndex]
  cityOptions.value = cityData[selectedProvince?.id] || []

  const cityIndex = Math.max(
    cityOptions.value.findIndex((item) => item.name === addressData.value.city),
    0,
  )
  const selectedCity = cityOptions.value[cityIndex]
  countyOptions.value = countyData[selectedCity?.id] || []

  const countyIndex = Math.max(
    countyOptions.value.findIndex((item) => item.name === addressData.value.region),
    0,
  )
  regionIndex.value = [provinceIndex, cityIndex, countyIndex]
}

// ===== onLoad =====
// 页面加载
onLoad((option) => {
  let title = '新增收货地址'
  if (option?.type === 'edit' && option?.id) {
    title = '编辑收货地址'
    loadAddressDetail(Number(option.id))
  }
  manageType.value = option?.type || 'add'
  uni.setNavigationBarTitle({ title })
})

// 加载地址详情（编辑模式）
const loadAddressDetail = async (id: number) => {
  try {
    const res = await fetchAddressDetailAPI(id)
    addressData.value = res.data
    syncRegionPicker()
  } catch (e) {
    console.error('加载地址详情失败', e)
  }
}

// ===== 事件处理方法 =====
// 默认地址开关切换
const handleSwitchChange = (e: UniHelper.SwitchOnChangeEvent) => {
  addressData.value.defaultStatus = e.detail.value ? 1 : 0
}

// 省、市、区三级地区选择
const handleRegionColumnChange = (e: { detail: { column: number; value: number } }) => {
  const { column, value } = e.detail
  const nextIndex = [...regionIndex.value]
  nextIndex[column] = value

  if (column === 0) {
    const selectedProvince = provinceOptions[value]
    cityOptions.value = cityData[selectedProvince?.id] || []
    countyOptions.value = countyData[cityOptions.value[0]?.id] || []
    nextIndex[1] = 0
    nextIndex[2] = 0
  } else if (column === 1) {
    const selectedCity = cityOptions.value[value]
    countyOptions.value = countyData[selectedCity?.id] || []
    nextIndex[2] = 0
  }

  regionIndex.value = nextIndex
}

const handleRegionChange = (e: { detail: { value: number[] } }) => {
  const [provinceIndex, cityIndex, countyIndex] = e.detail.value
  const province = provinceOptions[provinceIndex]
  const city = cityOptions.value[cityIndex]
  const county = countyOptions.value[countyIndex]
  if (!province || !city || !county) return

  regionIndex.value = [provinceIndex, cityIndex, countyIndex]
  addressData.value.province = province.name
  addressData.value.city = city.name
  addressData.value.region = county.name
}

// 提交表单
const handleConfirm = async () => {
  const data = addressData.value
  if (!data.name) {
    uni.showToast({ title: '请填写收货人姓名', icon: 'none' })
    return
  }
  if (!/(^1[3|4|5|7|8][0-9]{9}$)/.test(data.phoneNumber)) {
    uni.showToast({ title: '请输入正确的手机号码', icon: 'none' })
    return
  }
  if (!data.province || !data.city || !data.region) {
    uni.showToast({ title: '请选择所在区域', icon: 'none' })
    return
  }
  if (!data.detailAddress) {
    uni.showToast({ title: '请填写详细地址信息', icon: 'none' })
    return
  }

  try {
    const payload: MemberReceiveAddress = { ...data }
    delete payload.postCode
    if (manageType.value === 'edit') {
      await updateAddressAPI(payload)
      uni.showToast({ title: '地址修改成功！' })
    } else {
      await addAddressAPI(payload)
      uni.showToast({ title: '地址添加成功！' })
    }
    // 刷新上一页地址列表
    const pages = getCurrentPages()
    const prevPage = pages[pages.length - 2] as any
    if (prevPage && prevPage.refreshList) {
      prevPage.refreshList(payload, manageType.value)
    }
    setTimeout(() => {
      uni.navigateBack()
    }, 800)
  } catch (e) {
    console.error('保存地址失败', e)
  }
}
</script>

<style lang="scss">
page {
  background: $page-color-base;
  padding-top: 16rpx;
}
</style>

<style lang="scss" scoped>
.row {
  display: flex;
  align-items: center;
  position: relative;
  padding: 0 30rpx;
  height: 110rpx;
  background: #fff;

  .tit {
    flex-shrink: 0;
    width: 150rpx;
    font-size: 30rpx;
    color: $font-color-dark;
  }

  .input {
    flex: 1;
    font-size: 30rpx;
    color: $font-color-dark;
  }

  .region-picker {
    flex: 1;
    min-width: 0;
  }

  .region-picker-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-width: 0;
    height: 110rpx;
  }

  .region-value {
    overflow: hidden;
    flex: 1;
    font-size: 30rpx;
    color: $font-color-dark;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .region-placeholder {
    flex: 1;
    font-size: 30rpx;
    color: $font-color-light;
  }

  .region-arrow {
    flex-shrink: 0;
    margin-left: 16rpx;
    font-size: 42rpx;
    color: $font-color-light;
  }

  .icon-shouhuodizhi {
    font-size: 36rpx;
    color: $font-color-light;
  }
}

.default-row {
  margin-top: 16rpx;

  .tit {
    flex: 1;
  }

  switch {
    transform: translateX(16rpx) scale(0.9);
  }
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 690rpx;
  height: 80rpx;
  margin: 60rpx auto;
  font-size: $font-lg;
  color: #fff;
  background-color: $base-color;
  border-radius: 10rpx;
  box-shadow: 1px 2px 5px rgba(219, 63, 96, 0.4);
}
</style>
