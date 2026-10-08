<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { guestApi } from '@/api/guests'
import { scheduleApi } from '@/api/schedules'
import { useAuthStore } from '@/stores/auth'
import { GENDER_LABELS, toOptions } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Dẫn khách (guest = null) hoặc sửa thông tin khách (guest = object), dùng chung 1 form.
// Thành viên: người dẫn luôn là chính mình. ADMIN/TREASURER (manager): chọn người dẫn trong số người đang đăng ký
// buổi, hoặc để trống (khách tự liên hệ CLB).
const props = defineProps({
  // Cần id, playDate; khi thêm cần thêm maxPlayers, registeredCount
  schedule: { type: Object, required: true },
  guest: { type: Object, default: null },
  manager: { type: Boolean, default: false }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const auth = useAuthStore()
const isEdit = computed(() => !!props.guest)
const formRef = ref()
const saving = ref(false)
const genderOptions = toOptions(GENDER_LABELS)
const participants = ref([])
const participantsLoading = ref(false)

const emptyForm = () => ({ fullName: '', phone: '', gender: null, note: '', invitedById: null })
const form = reactive(emptyForm())
const serverErrors = reactive({})

const rules = {
  fullName: [{ required: true, message: 'Vui lòng nhập họ tên khách', trigger: 'blur' }],
  phone: [
    { pattern: /^0\d{9,10}$/, message: 'Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số', trigger: 'blur' }
  ]
}

const slotsLeft = computed(() => Math.max(props.schedule.maxPlayers - props.schedule.registeredCount, 0))
// Người dẫn hiện tại có thể không còn trong danh sách đăng ký (dữ liệu cũ): vẫn hiện để giữ lựa chọn
const inviterOptions = computed(() => {
  const g = props.guest
  const list = participants.value.map((p) => ({ id: p.userId, name: p.fullName }))
  if (g?.invitedById && !list.some((p) => p.id === g.invitedById)) {
    list.unshift({ id: g.invitedById, name: `${g.invitedByName} (không còn đăng ký)` })
  }
  return list
})

async function loadParticipants() {
  participantsLoading.value = true
  try {
    participants.value = await scheduleApi.participants(props.schedule.id)
    // Thêm mới: mặc định người dẫn là chính mình nếu mình đang đăng ký buổi này
    if (!isEdit.value && participants.value.some((p) => p.userId === auth.user?.id)) form.invitedById = auth.user.id
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    participantsLoading.value = false
  }
}

watch(visible, (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm())
  if (props.guest) {
    const g = props.guest
    Object.assign(form, {
      fullName: g.fullName,
      phone: g.phone ?? '',
      gender: g.gender ?? null,
      note: g.note ?? '',
      invitedById: g.invitedById ?? null
    })
  }
  if (props.manager) loadParticipants()
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  const payload = {
    fullName: form.fullName,
    phone: form.phone || null,
    gender: form.gender || null,
    note: form.note || null,
    invitedById: props.manager ? form.invitedById : null
  }
  saving.value = true
  try {
    const saved = isEdit.value
      ? await guestApi.update(props.schedule.id, props.guest.id, payload)
      : await guestApi.add(props.schedule.id, payload)
    ElMessage.success(isEdit.value
      ? `Đã cập nhật thông tin khách "${saved.fullName}"`
      : `Đã thêm khách "${saved.fullName}" — phí ${formatCurrency(saved.fee)}`)
    visible.value = false
    emit('saved', saved)
  } catch (err) {
    Object.assign(serverErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? 'Sửa thông tin khách' : 'Dẫn khách vãng lai'"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
    class="guest-form-dialog"
  >
    <p v-if="isEdit" class="text-secondary intro">
      Thông tin khách dùng chung cho mọi buổi khách này tham gia. Phí khách
      <template v-if="guest.fee != null">({{ formatCurrency(guest.fee) }})</template> và tình trạng thu tiền không đổi.
    </p>
    <p v-else class="text-secondary intro">
      Còn {{ slotsLeft }} chỗ. Khách chiếm 1 chỗ trong buổi và đóng phí khách theo mức áp dụng ngày
      {{ schedule.playDate }}. Khách từng đến (cùng số điện thoại) sẽ dùng lại thông tin cũ.
    </p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="Họ tên khách" prop="fullName" :error="serverErrors.fullName">
        <el-input v-model="form.fullName" maxlength="100" placeholder="VD: Nguyễn Văn B" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="Số điện thoại" prop="phone" :error="serverErrors.phone">
            <el-input v-model.trim="form.phone" maxlength="11" placeholder="Không bắt buộc" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="Giới tính" prop="gender" :error="serverErrors.gender">
            <el-radio-group v-model="form.gender">
              <el-radio-button v-for="g in genderOptions" :key="g.value" :value="g.value">{{ g.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="manager" label="Người dẫn khách" prop="invitedById" :error="serverErrors.invitedById">
        <el-select
          v-model="form.invitedById"
          :loading="participantsLoading"
          clearable
          filterable
          placeholder="Không có (khách tự liên hệ CLB)"
          no-data-text="Chưa có thành viên nào đăng ký buổi này"
          class="full-width"
        >
          <el-option
            v-for="p in inviterOptions"
            :key="p.id"
            :value="p.id"
            :label="p.id === auth.user?.id ? `${p.name} (bạn)` : p.name"
          />
        </el-select>
        <div class="text-secondary hint">
          Chỉ chọn được thành viên đã đăng ký buổi này. Để trống nếu khách tự liên hệ CLB. Người dẫn chịu trách nhiệm
          nếu khách chưa trả tiền.
        </div>
      </el-form-item>
      <p v-else class="text-secondary inviter">
        Người dẫn: <strong>bạn</strong> — bạn chịu trách nhiệm nếu khách chưa trả tiền.
      </p>
      <el-form-item label="Ghi chú" prop="note" :error="serverErrors.note">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Thêm khách' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.intro {
  margin: 0 0 12px;
}

.inviter {
  margin: 0 0 16px;
}

.hint {
  margin-top: 4px;
  line-height: 1.4;
}

:global(.guest-form-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
