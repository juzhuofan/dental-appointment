<script>
import { bootstrapAuth, resumeAuth } from './utils/auth';

export default {
  onLaunch() { bootstrapAuth(); },
  onShow() { resumeAuth(); }
};
</script>

<style>
@import './styles/app.css';
</style>
